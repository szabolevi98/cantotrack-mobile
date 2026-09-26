package net.levente.cantotrack.mobile.data.timer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.levente.cantotrack.mobile.MainActivity
import net.levente.cantotrack.mobile.R

/**
 * The running clock in the notification shade: the ticket, a counter the
 * system keeps going by itself, and a button that stops it. No service runs
 * for it — the clock is on the server, and the counter is the notification's.
 */
class TimerNotifier(private val context: Context) {

    fun show(clock: RunningClock) {
        if (!allowed()) return
        ensureChannel()

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TICKET, clock.ticket),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(clock.ticket)
            .setContentText(clock.title)
            .setWhen(clock.startedWallMillis())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setColor(ContextCompat.getColor(context, R.color.ct_primary))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.timer_stop), stop)
            .build()

        @Suppress("MissingPermission") // checked in allowed()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    fun hide() {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.timer_channel_description)
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL = "timer"
        const val ID = 1
    }
}
