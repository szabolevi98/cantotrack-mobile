package net.levente.cantotrack.mobile.data.timer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.CantoTrackApp
import net.levente.cantotrack.mobile.MainActivity
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.ui.formatMinutes

/**
 * The clock on the home screen: the ticket it runs on, counting, today's
 * hours, and a button that stops it — or starts it again on the ticket it
 * ran on last. Like the notification it needs no service: the counter is the
 * launcher's own Chronometer, started where the server says the clock did.
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        val container = (context.applicationContext as CantoTrackApp).container
        val pending = goAsync()
        scope.launch {
            // The clock may have been started or stopped on the web meanwhile; a refresh redraws.
            BackgroundClock.refresh(container)
            render(context, manager, ids)
            pending.finish()
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Draws every widget again: the clock, or today's hours, changed. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val container = (context.applicationContext as CantoTrackApp).container
            val clock = container.timer.clock.value
            val prefs = container.prefs
            val views = RemoteViews(context.packageName, R.layout.widget_clock)

            views.setTextViewText(
                R.id.widget_today,
                prefs.todayMinutes?.let { context.getString(R.string.today_logged, formatMinutes(context, it)) }.orEmpty(),
            )

            when {
                container.sessions.current == null -> {
                    views.setTextViewText(R.id.widget_label, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_ticket, context.getString(R.string.widget_signed_out))
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_signed_out_text))
                    views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                    views.setViewVisibility(R.id.widget_button, View.GONE)
                    views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
                }
                clock != null -> {
                    views.setTextViewText(R.id.widget_label, context.getString(R.string.clock_running))
                    views.setTextViewText(R.id.widget_ticket, clock.ticket)
                    views.setTextViewText(R.id.widget_title, clock.title)
                    views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE)
                    views.setChronometer(R.id.widget_chronometer, clock.sinceElapsed, null, true)
                    views.setViewVisibility(R.id.widget_button, View.VISIBLE)
                    views.setTextViewText(R.id.widget_button, context.getString(R.string.timer_stop))
                    views.setOnClickPendingIntent(R.id.widget_button, broadcast(context, TimerActionReceiver.ACTION_STOP, null))
                    views.setOnClickPendingIntent(R.id.widget_root, openApp(context, clock.ticket))
                }
                else -> {
                    val last = prefs.lastClockTicket
                    views.setTextViewText(R.id.widget_label, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_ticket, context.getString(R.string.widget_not_running))
                    views.setTextViewText(
                        R.id.widget_title,
                        if (last == null) context.getString(R.string.widget_pick_ticket) else listOfNotNull(last, prefs.lastClockTitle).joinToString(" · "),
                    )
                    views.setChronometer(R.id.widget_chronometer, 0, null, false)
                    views.setViewVisibility(R.id.widget_chronometer, View.GONE)
                    if (last == null) {
                        views.setViewVisibility(R.id.widget_button, View.GONE)
                    } else {
                        views.setViewVisibility(R.id.widget_button, View.VISIBLE)
                        views.setTextViewText(R.id.widget_button, context.getString(R.string.widget_start_on, last))
                        views.setOnClickPendingIntent(R.id.widget_button, broadcast(context, TimerActionReceiver.ACTION_START, last))
                    }
                    views.setOnClickPendingIntent(R.id.widget_root, openApp(context, null))
                }
            }

            manager.updateAppWidget(ids, views)
        }

        private fun broadcast(context: Context, action: String, ticket: String?): PendingIntent = PendingIntent.getBroadcast(
            context,
            if (action == TimerActionReceiver.ACTION_STOP) 10 else 11,
            Intent(context, TimerActionReceiver::class.java).setAction(action).putExtra(TimerActionReceiver.EXTRA_TICKET, ticket),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openApp(context: Context, ticket: String?): PendingIntent = PendingIntent.getActivity(
            context,
            12,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TICKET, ticket),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
