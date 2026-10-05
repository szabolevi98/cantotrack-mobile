package net.levente.cantotrack.mobile.data.timer

import android.content.Context
import kotlinx.coroutines.flow.first
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.session.SessionState
import net.levente.cantotrack.mobile.ui.formatMinutes
import java.time.LocalDate

/**
 * The clock driven from outside the app — the notification's button, the
 * widget, the quick settings tile — and what came of it, as a sentence for a
 * toast. The app's own screens go through [net.levente.cantotrack.mobile.ui.ClockActions].
 */
object BackgroundClock {

    /** Stops the clock and logs it. */
    suspend fun stop(context: Context, container: AppContainer): String = attempt(context, container) { session ->
        val logged = container.timer.stop(session, null)
        // Today's hours changed: the widget shows them.
        val today = LocalDate.now().toString()
        runCatching { container.todayLogged(container.api.worklogs(session, today, today).sumOf { it.minutes }) }
        if (logged == null) {
            context.getString(R.string.timer_under_a_minute)
        } else {
            context.getString(R.string.timer_logged, formatMinutes(context, logged.minutes), logged.ticket.orEmpty())
        }
    }

    /** Starts it on [key]; one running elsewhere is logged first. */
    suspend fun start(context: Context, container: AppContainer, key: String): String = attempt(context, container) { session ->
        val started = container.timer.start(session, key)
        started.logged?.let { context.getString(R.string.timer_switched, formatMinutes(context, it.minutes), it.ticket.orEmpty()) }
            ?: context.getString(R.string.timer_started, key)
    }

    /** Reads the server's clock again, quietly: the web may have started or stopped it. */
    suspend fun refresh(container: AppContainer) {
        val session = signedIn(container) ?: return container.timer.forget()
        try {
            container.timer.refresh(session)
        } catch (e: ApiException.Unauthorized) {
            container.sessions.expire()
        } catch (e: ApiException) {
            // Offline: the last known clock stays.
        }
    }

    /** The session, once the stored one has been read — the process may have just started. */
    suspend fun signedIn(container: AppContainer) =
        (container.sessions.state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session?.connection()

    private suspend fun attempt(
        context: Context,
        container: AppContainer,
        block: suspend (net.levente.cantotrack.mobile.data.api.Connection) -> String,
    ): String {
        val session = signedIn(container) ?: run {
            container.timer.forget()
            return context.getString(R.string.error_signed_out)
        }
        return try {
            block(session)
        } catch (e: ApiException.Unauthorized) {
            container.sessions.expire()
            context.getString(R.string.error_session_expired)
        } catch (e: ApiException) {
            e.message?.takeIf { it.isNotBlank() && e is ApiException.Http } ?: context.getString(R.string.error_network)
        }
    }
}
