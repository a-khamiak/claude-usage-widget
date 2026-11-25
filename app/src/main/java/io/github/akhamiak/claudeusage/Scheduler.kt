package io.github.akhamiak.claudeusage

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

enum class Reason { PERIODIC, RESET, BACKOFF, TAP, CHECK_FIX, SIGN_IN }

/** Non-secret status persisted in private prefs. */
class StatusStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("status", Context.MODE_PRIVATE)

    fun load(): Status {
        val has = p.getBoolean("r_has", false)
        val reading = if (!has) null else Reading(
            p.getInt("r_s", 0), p.getLong("r_sr", 0).takeIf { it > 0 },
            p.getInt("r_w", 0), p.getLong("r_wr", 0).takeIf { it > 0 },
        )
        return Status(
            everSignedIn = p.getBoolean("signed_in_once", false),
            reading = reading,
            lastSuccessAt = p.getLong("ok_at", 0),
            lastFetchAt = p.getLong("fetch_at", 0),
            lastError = runCatching { LastError.valueOf(p.getString("err", "NONE")!!) }.getOrDefault(LastError.NONE),
            invalidStreak = p.getInt("invalid", 0),
            backoffLevel = p.getInt("backoff", 0),
            nextAllowedAt = p.getLong("next_at", 0),
            sessionChangedAt = p.getLong("changed_at", 0),
            refreshingSince = p.getLong("refreshing", 0),
            scopeUpgradeNeeded = p.getBoolean("scope_up", false),
            lastResult = p.getString("result", "") ?: "",
        )
    }

    fun save(s: Status) {
        p.edit()
            .putBoolean("signed_in_once", s.everSignedIn)
            .putBoolean("r_has", s.reading != null)
            .putInt("r_s", s.reading?.sessionPct ?: 0)
            .putLong("r_sr", s.reading?.sessionResetsAt ?: 0)
            .putInt("r_w", s.reading?.weekPct ?: 0)
            .putLong("r_wr", s.reading?.weekResetsAt ?: 0)
            .putLong("ok_at", s.lastSuccessAt)
            .putLong("fetch_at", s.lastFetchAt)
            .putString("err", s.lastError.name)
            .putInt("invalid", s.invalidStreak)
            .putInt("backoff", s.backoffLevel)
            .putLong("next_at", s.nextAllowedAt)
            .putLong("changed_at", s.sessionChangedAt)
            .putLong("refreshing", s.refreshingSince)
            .putBoolean("scope_up", s.scopeUpgradeNeeded)
            .putString("result", s.lastResult)
            .commit()
    }

    fun reset() {
        p.edit().clear().commit()
    }
}

/** One refresh pass: config → fetch (if allowed) → status → schedule → render. Serialised process-wide. */
object Engine {
    private val LOCK = Any()

    fun run(context: Context, reason: Reason) = synchronized(LOCK) {
        val app = context.applicationContext
        val configs = ConfigStore(app)
        configs.refreshIfDue(System.currentTimeMillis(), force = reason == Reason.CHECK_FIX)
        val cfg = configs.current()
        val tokens = KeystoreTokenStore(app)
        val statusStore = StatusStore(app)
        var s = statusStore.load()
        val now = System.currentTimeMillis()

        val blocked = cfg.disabled || cfg.updateRequired || !tokens.hasTokens() ||
            (s.lastError == LastError.SIGNED_OUT && reason != Reason.SIGN_IN)
        val waiting = s.nextAllowedAt > now && reason != Reason.SIGN_IN
        if (!blocked && !waiting) {
            s = s.copy(refreshingSince = now)
            statusStore.save(s)
            Widgets.renderAll(app)
            val http = Http()
            val auth = Auth(tokens, http, { cfg })
            val result = UsageClient(tokens, auth, http, { cfg }).fetch()
            s = StateMachine.apply(s, result, System.currentTimeMillis(), cfg.polling)
            statusStore.save(s)
        }
        Scheduler.reschedule(app, s, cfg)
        Widgets.renderAll(app)
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val reason = runCatching { Reason.valueOf(inputData.getString(KEY_REASON) ?: "") }.getOrDefault(Reason.PERIODIC)
        Engine.run(applicationContext, reason)
        return Result.success()
    }

    companion object {
        const val KEY_REASON = "reason"
    }
}

/** WorkManager periodic + one-off work, and the 5-minute non-wakeup re-render tick (§7). */
object Scheduler {
    private const val PERIODIC = "periodic"
    private const val RESET = "reset"
    private const val BACKOFF = "backoff"
    private const val NOW = "now"
    private const val TICK_MS = 5 * 60_000L

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("sched", Context.MODE_PRIVATE)

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private fun input(reason: Reason) = Data.Builder().putString(RefreshWorker.KEY_REASON, reason.name).build()

    /** Called when a widget is added/updated or after boot: keep existing schedules. */
    fun ensure(context: Context) {
        val cfg = ConfigStore(context).current()
        val min = prefs(context).getInt("periodic_min", cfg.polling.activeIntervalMin)
        enqueuePeriodic(context, min, ExistingPeriodicWorkPolicy.KEEP)
        startTick(context)
    }

    fun reschedule(context: Context, s: Status, cfg: AppConfig) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val min = StateMachine.intervalMin(s, now, cfg.polling)
        if (p.getInt("periodic_min", -1) != min) {
            enqueuePeriodic(context, min, ExistingPeriodicWorkPolicy.UPDATE)
            p.edit().putInt("periodic_min", min).apply()
        }
        val resetAt = StateMachine.nextResetRefreshAt(s.reading, now)
        if (resetAt != null && p.getLong("reset_at", 0) != resetAt) {
            oneOff(context, RESET, Reason.RESET, resetAt - now)
            p.edit().putLong("reset_at", resetAt).apply()
        }
        if (s.nextAllowedAt > now) oneOff(context, BACKOFF, Reason.BACKOFF, s.nextAllowedAt - now + 1_000)
        startTick(context)
    }

    /** Immediate fetch (tap, retry). KEEP: a second tap while one is queued does nothing. No network constraint, so offline shows at once. */
    fun enqueueNow(context: Context, reason: Reason) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(input(reason)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        listOf(PERIODIC, RESET, BACKOFF, NOW).forEach { wm.cancelUniqueWork(it) }
        prefs(context).edit().clear().apply()
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    private fun enqueuePeriodic(context: Context, minutes: Int, policy: ExistingPeriodicWorkPolicy) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(network)
            .setInputData(input(Reason.PERIODIC))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, policy, req)
    }

    private fun oneOff(context: Context, name: String, reason: Reason, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(network)
            .setInputData(input(reason))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    /** Non-wakeup inexact alarm: re-renders countdowns only while the phone is awake. No network. */
    private fun startTick(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + TICK_MS,
            TICK_MS,
            tickIntent(context),
        )
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, RefreshReceiver.RC_TICK,
        Intent(context, RefreshReceiver::class.java).putExtra(RefreshReceiver.EXTRA_OP, RefreshReceiver.OP_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
