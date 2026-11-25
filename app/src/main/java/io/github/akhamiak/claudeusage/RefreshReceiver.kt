package io.github.akhamiak.claudeusage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/** Tap target and re-render tick. Not exported; reached only through our own immutable PendingIntents. */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(EXTRA_OP)) {
            OP_TICK -> Widgets.renderAll(context)
            OP_TAP -> onTap(context)
        }
    }

    private fun onTap(context: Context) {
        val app = context.applicationContext
        val store = StatusStore(app)
        val now = System.currentTimeMillis()
        val s = store.load().copy(sessionChangedAt = now) // a tap puts polling back on the active interval
        store.save(s)
        val cfg = ConfigStore(app).current()
        val paused = s.lastError == LastError.RATE_LIMITED && s.nextAllowedAt > now
        val cooledDown = now - s.lastFetchAt >= cfg.polling.tapCooldownS * 1000L
        when {
            paused -> Widgets.renderAll(app) // shows the next-try time; no extra call
            cooledDown -> Scheduler.enqueueNow(app, Reason.TAP)
            else -> {
                // Within the cooldown: re-render (countdowns) with a brief refreshing dot.
                Widgets.renderAll(app, forceRefreshing = true)
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Widgets.renderAll(app)
                    } finally {
                        pending.finish()
                    }
                }, 700)
            }
        }
    }

    companion object {
        const val EXTRA_OP = "op"
        const val OP_TAP = "tap"
        const val OP_TICK = "tick"
        const val RC_TICK = 20
    }
}
