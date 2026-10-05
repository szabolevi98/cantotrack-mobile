package net.levente.cantotrack.mobile.data.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.levente.cantotrack.mobile.CantoTrackApp
import net.levente.cantotrack.mobile.MainActivity
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.Prefs
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Connection
import net.levente.cantotrack.mobile.data.api.Notification
import net.levente.cantotrack.mobile.data.timer.BackgroundClock
import java.util.concurrent.TimeUnit

/**
 * What the bell says, on the phone: how many are unread — the number on the
 * tab — and, now and then in the background, the new ones in the
 * notification shade.
 *
 * The server has no way of reaching a phone by itself (no push service), so
 * the app asks: every quarter of an hour in the background, with the system
 * choosing the moment to save the battery, and every minute while it is open.
 */
class NewsTracker(private val api: ApiClient, private val prefs: Prefs, private val notifier: NewsNotifier) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** How many are unread — the cheapest question: one notification to a page. */
    suspend fun refresh(connection: Connection) {
        _unread.value = api.notifications(connection, unreadOnly = true, perPage = 1).meta.unread
    }

    fun set(unread: Int) {
        _unread.value = unread
    }

    /**
     * The unread ones not shown yet, into the shade. The very first look only
     * notes where things stand: a phone that just signed in is not shown a
     * month of old news.
     */
    suspend fun check(connection: Connection) {
        val page = api.notifications(connection, unreadOnly = true, perPage = 20)
        _unread.value = page.meta.unread
        val newest = page.data.maxOfOrNull { it.id } ?: 0
        val seen = prefs.lastSeenNotification

        if (seen >= 0 && prefs.newsAlerts) {
            page.data.filter { it.id > seen }.sortedBy { it.id }.forEach(notifier::show)
        }
        if (newest > seen) prefs.lastSeenNotification = newest
    }

    fun forget() {
        _unread.value = 0
        notifier.clear()
    }

    /** The bell was opened in the app: the shade's copies are not needed. */
    fun clearShade() = notifier.clear()

    /** Whether the phone lets the app show them at all (Android 13 asks). */
    fun allowed() = notifier.allowed()
}

/** The bell's news in the notification shade, one a notification, grouped. */
class NewsNotifier(private val context: Context) {

    fun show(n: Notification) {
        if (!allowed()) return
        ensureChannel()

        // A ticket opens in the app; an epic, which the app does not show, on the web.
        val intent = if (n.ticket == null && n.url != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse(n.url)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TICKET, n.ticket?.key)
        }
        val open = PendingIntent.getActivity(context, n.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val title = n.ticket?.let { "${it.key} · ${it.title}" } ?: n.epic?.title ?: context.getString(R.string.app_name)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(n.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
            .setColor(ContextCompat.getColor(context, R.color.ct_primary))
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val summary = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.ct_primary))
            .setContentTitle(context.getString(R.string.app_name))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        @Suppress("MissingPermission") // checked in allowed()
        NotificationManagerCompat.from(context).apply {
            notify(TAG, n.id, notification)
            notify(TAG, SUMMARY_ID, summary)
        }
    }

    /** Read in the app, or signed out: the shade's copies go. */
    fun clear() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag == TAG }.forEach { manager.cancel(TAG, it.id) }
    }

    fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.news_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.news_channel_description)
            },
        )
    }

    private companion object {
        const val CHANNEL = "news"
        const val GROUP = "news"
        const val TAG = "news"
        const val SUMMARY_ID = 0
    }
}

/** The background look at the bell, every quarter of an hour or so. */
class NewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CantoTrackApp).container
        val connection = BackgroundClock.signedIn(container) ?: return Result.success()
        try {
            container.news.check(connection)
        } catch (e: ApiException.Unauthorized) {
            container.sessions.expire()
        } catch (e: ApiException) {
            // Offline, or the server is down: the next look will see it.
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "news"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
