package net.levente.cantotrack.mobile.data.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.CantoTrackApp
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.ui.formatMinutes

/** The notification's "Stop" button: stops the clock and logs it, without opening the app. */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val container = (context.applicationContext as CantoTrackApp).container
        val session = container.sessions.current ?: return container.timer.forget()
        val pending = goAsync()

        scope.launch {
            val message = try {
                val logged = container.timer.stop(session.connection(), null)
                if (logged == null) {
                    context.getString(R.string.timer_under_a_minute)
                } else {
                    context.getString(R.string.timer_logged, formatMinutes(context, logged.minutes), logged.ticket.orEmpty())
                }
            } catch (e: ApiException.Unauthorized) {
                container.sessions.expire()
                context.getString(R.string.error_session_expired)
            } catch (e: ApiException) {
                e.message?.takeIf { it.isNotBlank() && e is ApiException.Http } ?: context.getString(R.string.error_network)
            }
            launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "net.levente.cantotrack.mobile.STOP_TIMER"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
