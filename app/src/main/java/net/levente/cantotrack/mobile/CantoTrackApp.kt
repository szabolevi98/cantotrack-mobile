package net.levente.cantotrack.mobile

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.levente.cantotrack.mobile.data.Prefs
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.files.AttachmentFiles
import net.levente.cantotrack.mobile.data.news.NewsNotifier
import net.levente.cantotrack.mobile.data.news.NewsTracker
import net.levente.cantotrack.mobile.data.news.NewsWorker
import net.levente.cantotrack.mobile.data.session.SessionManager
import net.levente.cantotrack.mobile.data.session.SessionState
import net.levente.cantotrack.mobile.data.session.SessionStore
import net.levente.cantotrack.mobile.data.session.TokenCipher
import net.levente.cantotrack.mobile.data.timer.ClockTileService
import net.levente.cantotrack.mobile.data.timer.ClockWidget
import net.levente.cantotrack.mobile.data.timer.TimerNotifier
import net.levente.cantotrack.mobile.data.timer.TimerTracker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "cantotrack")

/** The app's few long-lived objects, built once; small enough to need no DI framework. */
class AppContainer(val context: Context) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Outlives every screen; session checks and sign-outs must not be cancelled by navigation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val prefs = Prefs(context, json)
    val api = ApiClient(http, json)
    val sessions = SessionManager(SessionStore(context.dataStore, TokenCipher(), json), api, appScope)
    val news = NewsTracker(api, prefs, NewsNotifier(context))
    val files = AttachmentFiles(context, api)

    val timer = TimerTracker(api, TimerNotifier(context)) { clock ->
        if (clock != null) {
            prefs.lastClockTicket = clock.ticket
            prefs.lastClockTitle = clock.title
        }
        ClockWidget.update(context)
        ClockTileService.refresh(context)
    }

    /** Today's hours, as a screen just read them: the widget shows them too. */
    fun todayLogged(minutes: Int) {
        if (prefs.todayMinutes == minutes) return
        prefs.todayMinutes = minutes
        ClockWidget.update(context)
    }
}

class CantoTrackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sessions.restore()

        container.appScope.launch {
            container.sessions.state.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> {
                        if (container.prefs.newsAlerts) NewsWorker.schedule(this@CantoTrackApp) else NewsWorker.cancel(this@CantoTrackApp)
                        shortcuts(true)
                    }
                    // A sign-out takes the clock's notification, the news and what the device kept of the account with it.
                    is SessionState.SignedOut -> {
                        container.timer.forget()
                        container.news.forget()
                        container.prefs.forgetAccount()
                        NewsWorker.cancel(this@CantoTrackApp)
                        ClockWidget.update(this@CantoTrackApp)
                        shortcuts(false)
                    }
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    /** The long press on the app's icon: log time, a new ticket, the clock. Only while signed in. */
    private fun shortcuts(signedIn: Boolean) {
        if (!signedIn) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }
        val shortcut = { id: String, label: Int, icon: Int, action: String ->
            ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(getString(label))
                .setIcon(IconCompat.createWithResource(this, icon))
                .setIntent(Intent(this, MainActivity::class.java).setAction(action).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                .build()
        }
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("log", R.string.shortcut_log_time, R.drawable.ic_shortcut_log, MainActivity.ACTION_LOG_TIME),
                    shortcut("new", R.string.shortcut_new_ticket, R.drawable.ic_shortcut_new, MainActivity.ACTION_NEW_TICKET),
                    shortcut("clock", R.string.shortcut_clock, R.drawable.ic_shortcut_clock, MainActivity.ACTION_CLOCK),
                ),
            )
        }
    }
}
