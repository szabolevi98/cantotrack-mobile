package net.levente.cantotrack.mobile.ui.hours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.data.session.SessionManager
import net.levente.cantotrack.mobile.ui.Minutes
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.timeInput
import net.levente.cantotrack.mobile.ui.toUiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class Day(val date: LocalDate, val entries: List<Worklog>) {
    val minutes: Int get() = entries.sumOf { it.minutes }
}

data class HoursState(
    /** The Monday of the week shown. */
    val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val days: List<Day> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** An entry being made or changed. */
    val saving: Boolean = false,
) {
    val sunday: LocalDate get() = monday.plusDays(6)
    val total: Int get() = days.sumOf { it.minutes }
    val isThisWeek: Boolean get() = !LocalDate.now().isBefore(monday) && !LocalDate.now().isAfter(sunday)
}

/** The second tab: one's own hours, a week at a time, day by day. */
class HoursViewModel(private val api: ApiClient, private val sessions: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(HoursState())
    val state: StateFlow<HoursState> = _state.asStateFlow()

    init {
        load()
    }

    fun previousWeek() = showWeek(state.value.monday.minusWeeks(1))

    fun nextWeek() = showWeek(state.value.monday.plusWeeks(1))

    fun thisWeek() = showWeek(HoursState().monday)

    /** The week a day picked from the calendar is in. */
    fun showWeekOf(day: LocalDate) = showWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    private fun showWeek(monday: LocalDate) {
        if (monday == state.value.monday) return
        _state.update { it.copy(monday = monday, days = emptyList()) }
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        val monday = state.value.monday
        _state.update { it.copy(loading = !refreshing && it.days.isEmpty(), refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val entries = sessions.call { api.worklogs(it, monday.toString(), monday.plusDays(6).toString()) }
                if (monday != state.value.monday) return@launch
                val byDay = entries.groupBy { LocalDate.parse(it.date) }
                // Every day that has hours, and the weekdays that have none: an empty Tuesday is worth seeing.
                val days = (0L..6L).map { monday.plusDays(it) }
                    .filter { day -> byDay.containsKey(day) || (day.dayOfWeek.value <= 5 && !day.isAfter(LocalDate.now())) }
                    .map { day -> Day(day, byDay[day].orEmpty().sortedWith(compareBy(nullsLast()) { it.start })) }
                    .reversed()
                _state.update { it.copy(days = days) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    /** For picking a ticket: one's own open ones, or every ticket matching [query]. */
    suspend fun searchTickets(query: String): List<Ticket> = sessions.call {
        if (query.isBlank()) api.tickets(it, "", mineOnly = true).data else api.tickets(it, query, mineOnly = false, openOnly = false).data
    }

    fun log(key: String, minutes: Int, date: String, note: String, onDone: (Boolean) -> Unit) = save(onDone) {
        val entry = sessions.call { api.logWork(it, key, timeInput(minutes), date, note) }
        UiText.Res(R.string.worklog_logged, listOf(Minutes(entry.minutes), key))
    }

    fun update(entry: Worklog, minutes: Int, date: String, note: String, onDone: (Boolean) -> Unit) = save(onDone) {
        val changed = sessions.call { api.updateWorklog(it, entry.id, timeInput(minutes), date, note) }
        UiText.Res(R.string.worklog_changed, listOf(Minutes(changed.minutes), changed.ticket))
    }

    private fun save(onDone: (Boolean) -> Unit, block: suspend () -> UiText) {
        if (state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = try {
                val message = block()
                _state.update { it.copy(message = message) }
                load(refreshing = false)
                true
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                false
            } finally {
                _state.update { it.copy(saving = false) }
            }
            onDone(saved)
        }
    }

    fun delete(entry: Worklog) {
        viewModelScope.launch {
            try {
                sessions.call { api.deleteWorklog(it, entry.id) }
                _state.update { s -> s.copy(days = s.days.map { d -> d.copy(entries = d.entries.filter { it.id != entry.id }) }) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
