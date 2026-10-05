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

/**
 * The buttons outside the app — the notification's Stop, the widget's Stop
 * and Start — which stop or start the clock without opening it.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as CantoTrackApp).container
        val pending = goAsync()

        scope.launch {
            val message = when (intent.action) {
                ACTION_STOP -> BackgroundClock.stop(context, container)
                ACTION_START -> intent.getStringExtra(EXTRA_TICKET)?.let { BackgroundClock.start(context, container, it) }
                else -> null
            }
            launch(Dispatchers.Main) {
                if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "net.levente.cantotrack.mobile.STOP_TIMER"
        const val ACTION_START = "net.levente.cantotrack.mobile.START_TIMER"
        const val EXTRA_TICKET = "ticket"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
