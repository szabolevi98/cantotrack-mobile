package net.levente.cantotrack.mobile.data.timer

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.Connection
import net.levente.cantotrack.mobile.data.api.LoggedTime
import net.levente.cantotrack.mobile.data.api.Timer
import net.levente.cantotrack.mobile.data.api.TimerStarted

/**
 * The clock as the app last saw it on the server.
 *
 * [sinceElapsed] is where it started on the phone's elapsedRealtime clock:
 * the server says how many seconds it has run, and the app counts on from
 * there, so a phone clock that is minutes off does not show wrong minutes.
 */
data class RunningClock(val ticket: String, val title: String, val sinceElapsed: Long) {
    fun seconds(now: Long = SystemClock.elapsedRealtime()): Long = ((now - sinceElapsed) / 1000).coerceAtLeast(0)

    /** When it started on the wall clock, for the notification's own counter. */
    fun startedWallMillis(): Long = System.currentTimeMillis() - seconds() * 1000

    companion object {
        fun from(timer: Timer): RunningClock =
            RunningClock(timer.ticket, timer.title, SystemClock.elapsedRealtime() - timer.seconds * 1000)
    }
}

/**
 * The one running clock, shared by every screen, the notification, the
 * widget and the quick settings tile. The server's is the real one — the web
 * can start and stop it too — so this is read again whenever the app comes
 * back to the front.
 */
class TimerTracker(
    private val api: ApiClient,
    private val notifier: TimerNotifier,
    /** Told of every change: the widget and the tile follow the clock. */
    private val onChange: (RunningClock?) -> Unit = {},
) {
    private val _clock = MutableStateFlow<RunningClock?>(null)
    val clock: StateFlow<RunningClock?> = _clock.asStateFlow()

    suspend fun refresh(connection: Connection) {
        set(api.timer(connection)?.let(RunningClock::from))
    }

    suspend fun start(connection: Connection, key: String): TimerStarted {
        val started = api.startTimer(connection, key)
        set(started.data?.let(RunningClock::from))
        return started
    }

    /** Null when it ran under a minute, and nothing was logged. */
    suspend fun stop(connection: Connection, note: String?): LoggedTime? {
        try {
            return api.stopTimer(connection, note)
        } finally {
            // Stopped, or already stopped elsewhere: either way the app shows none.
            runCatching { refresh(connection) }.onFailure { set(null) }
        }
    }

    suspend fun discard(connection: Connection) {
        api.discardTimer(connection)
        set(null)
    }

    /** Signed out: the clock may run on in the web, but this device shows nothing of it. */
    fun forget() = set(null)

    private fun set(clock: RunningClock?) {
        _clock.value = clock
        if (clock == null) notifier.hide() else notifier.show(clock)
        onChange(clock)
    }
}
