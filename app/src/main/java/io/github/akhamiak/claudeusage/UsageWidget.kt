package io.github.akhamiak.claudeusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.RemoteViews
import java.time.ZoneId

/** The home-screen widget provider. Exported (the system needs it) with APPWIDGET_UPDATE only. */
class UsageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Scheduler.ensure(context)
        Widgets.renderAll(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Widgets.renderAll(context) // re-render at the new size
    }

    override fun onEnabled(context: Context) {
        Scheduler.ensure(context)
        Scheduler.enqueueNow(context, Reason.PERIODIC)
    }

    override fun onDisabled(context: Context) {
        Scheduler.cancelAll(context)
    }
}

/** Renders every placed widget from the current stored state. No network. */
object Widgets {

    fun currentState(context: Context, forceRefreshing: Boolean = false): WidgetState {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val s = StatusStore(app).load().let { if (forceRefreshing) it.copy(refreshingSince = now) else it }
        return StateMachine.derive(s, ConfigStore(app).current(), now, KeystoreTokenStore(app).hasTokens())
    }

    fun renderAll(context: Context, forceRefreshing: Boolean = false) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, UsageWidget::class.java))
        if (ids.isEmpty()) return
        val state = currentState(app, forceRefreshing)
        val now = System.currentTimeMillis()
        ids.forEach { render(app, mgr, it, state, now) }
    }

    private fun render(context: Context, mgr: AppWidgetManager, id: Int, state: WidgetState, now: Long) {
        val res = context.resources
        val density = res.displayMetrics.density
        val opts = mgr.getAppWidgetOptions(id)
        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 160
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 160
        val corner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            res.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else {
            16f * density
        }
        val spec = RenderSpec(
            widthPx = (wDp * density).toInt(),
            heightPx = (hDp * density).toInt(),
            density = density,
            fontScale = res.configuration.fontScale,
            cornerPx = corner,
            is24h = DateFormat.is24HourFormat(context),
            zone = ZoneId.systemDefault(),
            now = now,
        )
        val views = RemoteViews(context.packageName, R.layout.widget)
        views.setImageViewBitmap(R.id.tile, WidgetRenderer.render(state, spec))
        views.setContentDescription(R.id.tile, Format.description(state, now))
        views.setOnClickPendingIntent(R.id.tile, tapIntent(context, state))
        mgr.updateAppWidget(id, views)
    }

    /** Explicit, immutable PendingIntents only (§4). */
    private fun tapIntent(context: Context, state: WidgetState): PendingIntent {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return when (state) {
            WidgetState.NotSetUp, WidgetState.SignedOut -> PendingIntent.getActivity(
                context, RC_SETUP,
                Intent(context, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
            WidgetState.EndpointChanged -> PendingIntent.getActivity(
                context, RC_CHECK_FIX,
                Intent(context, SetupActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SetupActivity.EXTRA_OP, SetupActivity.OP_CHECK_FIX),
                flags,
            )
            is WidgetState.KillSwitch -> PendingIntent.getActivity(
                context, RC_RELEASES,
                Intent(Intent.ACTION_VIEW, Uri.parse(ConfigStore.RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags,
            )
            else -> PendingIntent.getBroadcast(
                context, RC_TAP,
                Intent(context, RefreshReceiver::class.java).putExtra(RefreshReceiver.EXTRA_OP, RefreshReceiver.OP_TAP),
                flags,
            )
        }
    }

    private const val RC_SETUP = 10
    private const val RC_CHECK_FIX = 11
    private const val RC_RELEASES = 12
    private const val RC_TAP = 13
}
