package net.levente.cantotrack.mobile

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.session.SessionManager
import net.levente.cantotrack.mobile.data.session.SessionState
import net.levente.cantotrack.mobile.data.session.SessionStore
import net.levente.cantotrack.mobile.data.session.TokenCipher
import net.levente.cantotrack.mobile.data.timer.TimerNotifier
import net.levente.cantotrack.mobile.data.timer.TimerTracker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "cantotrack")

/** The app's few long-lived objects, built once; small enough to need no DI framework. */
class AppContainer(context: Context) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Outlives every screen; session checks and sign-outs must not be cancelled by navigation. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val api = ApiClient(http, json)
    val sessions = SessionManager(SessionStore(context.dataStore, TokenCipher(), json), api, appScope)
    val timer = TimerTracker(api, TimerNotifier(context))
}

class CantoTrackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sessions.restore()

        // A sign-out takes the clock's notification with it.
        container.appScope.launch {
            container.sessions.state.filterIsInstance<SessionState.SignedOut>().collect { container.timer.forget() }
        }
    }
}
