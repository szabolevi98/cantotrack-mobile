package net.levente.cantotrack.mobile.ui

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException

/**
 * Starting, stopping and throwing away the clock from any screen, with what
 * came of it said in the snackbar. [onLogged] runs after time was logged, so
 * a screen that shows hours can read them again.
 */
class ClockActions(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val context: Context,
    private val onLogged: () -> Unit,
) {
    var busy by mutableStateOf(false)
        private set

    fun start(key: String) = run {
        val started = container.sessions.call { container.timer.start(it, key) }
        started.logged?.let { context.getString(R.string.timer_switched, formatMinutes(context, it.minutes), it.ticket.orEmpty()) }
            ?.also { onLogged() }
            ?: context.getString(R.string.timer_started, key)
    }

    fun stop(note: String) = run {
        val logged = container.sessions.call { container.timer.stop(it, note) }
        if (logged == null) {
            context.getString(R.string.timer_under_a_minute)
        } else {
            onLogged()
            context.getString(R.string.timer_logged, formatMinutes(context, logged.minutes), logged.ticket.orEmpty())
        }
    }

    fun discard() = run {
        container.sessions.call { container.timer.discard(it) }
        context.getString(R.string.timer_discarded)
    }

    /** Reads the server's clock again: the web may have started or stopped it. */
    fun refresh() {
        scope.launch { runCatching { container.sessions.call { container.timer.refresh(it) } } }
    }

    private fun run(block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val message = try {
                block()
            } catch (e: ApiException) {
                when (e) {
                    is ApiException.Http -> e.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.error_server, e.status)
                    is ApiException.Network -> context.getString(R.string.error_network)
                    is ApiException.Unauthorized -> context.getString(R.string.error_session_expired)
                    is ApiException.BadResponse -> context.getString(R.string.error_bad_response)
                }
            } finally {
                busy = false
            }
            snackbar.showSnackbar(message)
        }
    }
}

@Composable
fun rememberClockActions(container: AppContainer, snackbar: SnackbarHostState, onLogged: () -> Unit = {}): ClockActions {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val logged by rememberUpdatedState(onLogged)
    return remember(container, snackbar) { ClockActions(container, scope, snackbar, context) { logged() } }
}
