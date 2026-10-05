package net.levente.cantotrack.mobile.data.timer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.levente.cantotrack.mobile.CantoTrackApp
import net.levente.cantotrack.mobile.MainActivity
import net.levente.cantotrack.mobile.R

/**
 * The clock in the quick settings: on while it runs, with the ticket under
 * its name. A tap stops it and logs the time — or, when it is not running,
 * starts it again on the ticket it ran on last. With no such ticket yet, the
 * tap opens the app.
 */
class ClockTileService : TileService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val container get() = (applicationContext as CantoTrackApp).container

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The shade was pulled down: drawn at once from what the app knows, and
     * read from the server again — at most once a minute, as the shade is
     * pulled down far more often than the clock changes.
     */
    override fun onStartListening() {
        super.onStartListening()
        draw()
        val now = System.currentTimeMillis()
        if (now - lastRefresh < 60_000) return
        lastRefresh = now
        scope.launch {
            withContext(Dispatchers.IO) { BackgroundClock.refresh(container) }
            draw()
        }
    }

    override fun onClick() {
        super.onClick()
        val running = container.timer.clock.value
        val last = container.prefs.lastClockTicket
        if (running == null && (last == null || container.sessions.current == null)) {
            openApp()
            return
        }
        qsTile?.let {
            it.state = Tile.STATE_UNAVAILABLE
            it.updateTile()
        }
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                if (running != null) BackgroundClock.stop(this@ClockTileService, container) else BackgroundClock.start(this@ClockTileService, container, last!!)
            }
            Toast.makeText(this@ClockTileService, message, Toast.LENGTH_LONG).show()
            draw()
        }
    }

    private fun draw() {
        val tile = qsTile ?: return
        val clock = container.timer.clock.value
        val last = container.prefs.lastClockTicket
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        tile.label = getString(R.string.tile_label)
        tile.state = if (clock != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = when {
            container.sessions.current == null -> getString(R.string.widget_signed_out)
            clock != null -> clock.ticket
            last != null -> getString(R.string.widget_start_on, last)
            else -> getString(R.string.widget_not_running)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.contentDescription = getString(R.string.tile_label) + ": " + subtitle
        tile.updateTile()
    }

    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 13, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private var lastRefresh = 0L

        /** The clock changed: the tile asks to be drawn again, if the shade is open. */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, ClockTileService::class.java)) }
        }
    }
}
