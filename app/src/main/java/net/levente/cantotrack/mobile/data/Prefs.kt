package net.levente.cantotrack.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** A query of the ticket list's query language, kept under a name of one's own. */
@Serializable
data class SavedQuery(val name: String, val query: String)

/**
 * The few things the phone remembers outside the session: read at once by
 * the widget, the quick settings tile and the background check, which
 * cannot wait for DataStore. Nothing secret is kept here — the token is in
 * [net.levente.cantotrack.mobile.data.session.SessionStore], encrypted.
 */
class Prefs(context: Context, private val json: Json) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cantotrack_prefs", Context.MODE_PRIVATE)

    /** The ticket the clock last ran on, for starting it again from the tile or the widget. */
    var lastClockTicket: String?
        get() = prefs.getString(LAST_TICKET, null)
        set(value) = prefs.edit { putString(LAST_TICKET, value) }

    var lastClockTitle: String?
        get() = prefs.getString(LAST_TITLE, null)
        set(value) = prefs.edit { putString(LAST_TITLE, value) }

    /** Whether news from the bell is shown in the notification shade. */
    var newsAlerts: Boolean
        get() = prefs.getBoolean(NEWS_ALERTS, true)
        set(value) = prefs.edit { putBoolean(NEWS_ALERTS, value) }

    /** The newest notification already shown in the shade; -1 before the first look. */
    var lastSeenNotification: Int
        get() = prefs.getInt(LAST_SEEN, -1)
        set(value) = prefs.edit { putInt(LAST_SEEN, value) }

    /** The board the board tab opens on. */
    var board: Int
        get() = prefs.getInt(BOARD, 0)
        set(value) = prefs.edit { putInt(BOARD, value) }

    /** The project a new ticket goes into, the one picked last. */
    var lastProject: String?
        get() = prefs.getString(LAST_PROJECT, null)
        set(value) = prefs.edit { putString(LAST_PROJECT, value) }

    var savedQueries: List<SavedQuery>
        get() = prefs.getString(QUERIES, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(SavedQuery.serializer()), it) }.getOrNull() }
            .orEmpty()
        set(value) = prefs.edit { putString(QUERIES, json.encodeToString(ListSerializer(SavedQuery.serializer()), value)) }

    /** Today's logged minutes, as the app last saw them — for the widget. Null on another day. */
    var todayMinutes: Int?
        get() = prefs.getInt(TODAY_MINUTES, -1).takeIf { it >= 0 && prefs.getString(TODAY_DATE, null) == LocalDate.now().toString() }
        set(value) = prefs.edit {
            putInt(TODAY_MINUTES, value ?: -1)
            putString(TODAY_DATE, LocalDate.now().toString())
        }

    /** Signed out: what belongs to the account goes, the settings stay. */
    fun forgetAccount() = prefs.edit {
        remove(LAST_TICKET)
        remove(LAST_TITLE)
        remove(LAST_SEEN)
        remove(BOARD)
        remove(LAST_PROJECT)
        remove(TODAY_MINUTES)
        remove(TODAY_DATE)
    }

    private companion object {
        const val LAST_TICKET = "last_clock_ticket"
        const val LAST_TITLE = "last_clock_title"
        const val NEWS_ALERTS = "news_alerts"
        const val LAST_SEEN = "last_seen_notification"
        const val BOARD = "board"
        const val LAST_PROJECT = "last_project"
        const val QUERIES = "saved_queries"
        const val TODAY_MINUTES = "today_minutes"
        const val TODAY_DATE = "today_date"
    }
}
