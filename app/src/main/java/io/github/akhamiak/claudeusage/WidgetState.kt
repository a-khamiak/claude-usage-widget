package io.github.akhamiak.claudeusage

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class LastError { NONE, OFFLINE, RATE_LIMITED, SERVER, SIGNED_OUT }

/** Everything the widget remembers between runs (non-secret). */
data class Status(
    val everSignedIn: Boolean = false,
    val reading: Reading? = null,
    val lastSuccessAt: Long = 0,
    val lastFetchAt: Long = 0,
    val lastError: LastError = LastError.NONE,
    val invalidStreak: Int = 0,
    val backoffLevel: Int = 0,
    val nextAllowedAt: Long = 0,
    val sessionChangedAt: Long = 0,
    val refreshingSince: Long = 0,
    val scopeUpgradeNeeded: Boolean = false,
    val lastResult: String = "",
)

enum class Window { SESSION, WEEK }

sealed class Mode {
    object Normal : Mode()
    /** Offline, server trouble, or data older than stale_after_min. ageMs < 0 means never fetched. */
    data class Stale(val ageMs: Long) : Mode()
    data class Paused(val nextTryAt: Long) : Mode()
}

sealed class WidgetState {
    object NotSetUp : WidgetState()
    object SignedOut : WidgetState()
    object EndpointChanged : WidgetState()
    data class KillSwitch(val message: String) : WidgetState()
    data class Limit(val window: Window, val resetsAt: Long?, val refreshing: Boolean) : WidgetState()
    data class Data(val reading: Reading?, val mode: Mode, val refreshing: Boolean) : WidgetState()
}

/** Pure state machine: Status + config + time → what the tile shows (§3.4). */
object StateMachine {
    const val WARN_PCT = 80
    const val REFRESHING_MAX_MS = 60_000L

    fun derive(s: Status, cfg: AppConfig, now: Long, hasTokens: Boolean): WidgetState {
        if (cfg.disabled || cfg.updateRequired) {
            return WidgetState.KillSwitch(cfg.message.ifEmpty { if (cfg.updateRequired) "UPDATE THE APP" else "" })
        }
        if (!hasTokens && !s.everSignedIn) return WidgetState.NotSetUp
        if (!hasTokens || s.lastError == LastError.SIGNED_OUT) return WidgetState.SignedOut
        if (s.invalidStreak >= 2) return WidgetState.EndpointChanged

        val refreshing = s.refreshingSince > 0 && now - s.refreshingSince < REFRESHING_MAX_MS
        val r = s.reading?.let { effective(it, now) }
        if (r != null) {
            val sessionLimit = r.sessionPct >= 100
            val weekLimit = r.weekPct >= 100
            if (sessionLimit || weekLimit) {
                val pickWeek = weekLimit && (!sessionLimit || (r.weekResetsAt ?: 0) >= (r.sessionResetsAt ?: 0))
                return if (pickWeek) WidgetState.Limit(Window.WEEK, r.weekResetsAt, refreshing)
                else WidgetState.Limit(Window.SESSION, r.sessionResetsAt, refreshing)
            }
        }
        val staleMs = cfg.polling.staleAfterMin * 60_000L
        val mode = when {
            s.lastError == LastError.RATE_LIMITED && s.nextAllowedAt > now -> Mode.Paused(s.nextAllowedAt)
            s.lastError == LastError.OFFLINE || s.lastError == LastError.SERVER ||
                s.lastSuccessAt == 0L || now - s.lastSuccessAt > staleMs ->
                Mode.Stale(if (s.lastSuccessAt == 0L) -1 else now - s.lastSuccessAt)
            else -> Mode.Normal
        }
        return WidgetState.Data(r, mode, refreshing)
    }

    /** A window whose reset time has passed reads 0% until the next fetch. */
    fun effective(r: Reading, now: Long): Reading = r.copy(
        sessionPct = if (r.sessionResetsAt != null && r.sessionResetsAt <= now) 0 else r.sessionPct,
        weekPct = if (r.weekResetsAt != null && r.weekResetsAt <= now) 0 else r.weekPct,
    )

    /** Applies a fetch result to the stored status (§5.4, §7). */
    fun apply(s: Status, result: FetchResult, now: Long, p: Polling): Status {
        val base = s.copy(lastFetchAt = now, refreshingSince = 0)
        return when (result) {
            is FetchResult.Ok -> base.copy(
                everSignedIn = true,
                reading = result.reading,
                lastSuccessAt = now,
                lastError = LastError.NONE,
                invalidStreak = 0,
                backoffLevel = 0,
                nextAllowedAt = 0,
                sessionChangedAt = if (s.reading?.sessionPct != result.reading.sessionPct || s.sessionChangedAt == 0L) now else s.sessionChangedAt,
                scopeUpgradeNeeded = false,
                lastResult = "OK",
            )
            is FetchResult.Invalid -> base.copy(
                invalidStreak = s.invalidStreak + 1,
                lastError = LastError.NONE,
                lastResult = "INVALID RESPONSE (${result.code})",
            )
            FetchResult.SignedOut -> base.copy(lastError = LastError.SIGNED_OUT, lastResult = "SIGNED OUT")
            FetchResult.ScopeMissing -> base.copy(
                lastError = LastError.SIGNED_OUT,
                scopeUpgradeNeeded = true,
                lastResult = "403 WITH user:profile",
            )
            is FetchResult.RateLimited -> base.copy(
                lastError = LastError.RATE_LIMITED,
                backoffLevel = s.backoffLevel + 1,
                nextAllowedAt = now + backoffMs(s.backoffLevel, p, result.retryAfterS),
                lastResult = "429",
            )
            is FetchResult.ServerError -> base.copy(
                lastError = LastError.SERVER,
                backoffLevel = s.backoffLevel + 1,
                nextAllowedAt = now + backoffMs(s.backoffLevel, p, result.retryAfterS),
                lastResult = "HTTP ${result.code}",
            )
            FetchResult.Offline -> base.copy(lastError = LastError.OFFLINE, lastResult = "OFFLINE")
        }
    }

    /** Exponential from backoff_start to backoff_max, never shorter than Retry-After. */
    fun backoffMs(level: Int, p: Polling, retryAfterS: Long?): Long {
        val exp = p.backoffStartMin.toLong() shl min(level, 16)
        val mins = min(exp, p.backoffMaxMin.toLong())
        return max(mins * 60_000L, (retryAfterS ?: 0) * 1000L)
    }

    /** Adaptive polling: 15 min while the session % moves, 60 min after 90 min without change. */
    fun intervalMin(s: Status, now: Long, p: Polling): Int =
        if (s.sessionChangedAt > 0 && now - s.sessionChangedAt >= p.idleAfterMin * 60_000L) p.idleIntervalMin
        else p.activeIntervalMin

    /** Earliest future reset + 1 min, for the one-off refresh (§7). */
    fun nextResetRefreshAt(r: Reading?, now: Long): Long? =
        listOfNotNull(r?.sessionResetsAt, r?.weekResetsAt).filter { it > now }.minOrNull()?.plus(60_000L)
}

/** Caption and label formatting (§3.2). Pure; time zone and 12/24 h passed in. */
object Format {
    private val DAYS = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /** Countdown rounded down to 5 min (`2H 10M`, `40M`); under 5 min shows exact minutes (D19, D22). */
    fun countdown(ms: Long): String {
        var m = max(0L, ms) / 60_000L
        if (m >= 5) m -= m % 5
        val h = m / 60
        val mm = m % 60
        return when {
            h == 0L -> "${mm}M"
            mm == 0L -> "${h}H"
            else -> "${h}H ${mm}M"
        }
    }

    fun clock(at: Long, zone: ZoneId, is24h: Boolean): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return if (is24h) String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
        else {
            val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
            String.format(Locale.US, "%d:%02d%s", h12, t.minute, if (t.hour < 12) "AM" else "PM")
        }
    }

    fun day(at: Long, zone: ZoneId): String = DAYS[Instant.ofEpochMilli(at).atZone(zone).dayOfWeek.value - 1]

    /** Session value line under `SESSION` (D22): `IN 2H 10M`, or `—` when no window is running. */
    fun sessionValue(resetsAt: Long?, now: Long): String =
        if (resetsAt == null || resetsAt <= now) "—" else "IN " + countdown(resetsAt - now)

    /** Limit tile value line under `SESSION RESETS` / `WEEK RESETS`: `IN 1H 40M`, or the day when > 24 h away. */
    fun limitValue(resetsAt: Long?, now: Long, zone: ZoneId): String = when {
        resetsAt == null || resetsAt <= now -> "—"
        resetsAt - now > 24 * 3_600_000L -> day(resetsAt, zone)
        else -> "IN " + countdown(resetsAt - now)
    }

    /** Week reset line (D15): `WED 14:00` when > 24 h away, else `IN 7H 25M`. */
    fun weekReset(resetsAt: Long?, now: Long, zone: ZoneId, is24h: Boolean): String = when {
        resetsAt == null || resetsAt <= now -> "—"
        resetsAt - now > 24 * 3_600_000L -> day(resetsAt, zone) + " " + clock(resetsAt, zone, is24h)
        else -> "IN " + countdown(resetsAt - now)
    }

    fun age(ms: Long): String {
        val m = max(0L, ms) / 60_000L
        return when {
            m < 60 -> "${m}M AGO"
            m < 24 * 60 -> "${m / 60}H AGO"
            else -> "${m / (24 * 60)}D AGO"
        }
    }

    /** Spoken reading for TalkBack (§4). */
    fun description(state: WidgetState, now: Long): String = when (state) {
        WidgetState.NotSetUp -> "Claude usage. Not set up. Tap to sign in."
        WidgetState.SignedOut -> "Claude usage. Session expired. Tap to sign in."
        WidgetState.EndpointChanged -> "Claude usage. Usage source changed. Tap to check for a fix."
        is WidgetState.KillSwitch -> "Claude usage paused. ${state.message}"
        is WidgetState.Limit -> "Claude usage. ${if (state.window == Window.SESSION) "Session" else "Weekly"} limit reached" +
            (state.resetsAt?.let { ", resets in ${spoken(it - now)}" } ?: "")
        is WidgetState.Data -> state.reading?.let { r ->
            val sr = r.sessionResetsAt?.takeIf { it > now }?.let { ", resets in ${spoken(it - now)}" } ?: ""
            val wr = r.weekResetsAt?.takeIf { it > now }?.let { ", resets in ${spoken(it - now)}" } ?: ""
            "Session ${r.sessionPct} percent$sr; week ${r.weekPct} percent$wr"
        } ?: "Claude usage. No data yet."
    }

    private fun spoken(ms: Long): String {
        val m = max(0L, ms) / 60_000L
        val d = m / 1440
        val h = (m % 1440) / 60
        val mm = m % 60
        return listOfNotNull(
            d.takeIf { it > 0 }?.let { "$it day${if (it == 1L) "" else "s"}" },
            h.takeIf { it > 0 }?.let { "$it hour${if (it == 1L) "" else "s"}" },
            mm.takeIf { it > 0 || (d == 0L && h == 0L) }?.let { "$it minute${if (it == 1L) "" else "s"}" },
        ).joinToString(" ")
    }
}
