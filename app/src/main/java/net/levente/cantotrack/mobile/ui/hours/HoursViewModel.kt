package net.levente.cantotrack.mobile.ui.hours

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Absence
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.Week
import net.levente.cantotrack.mobile.data.api.WeekDay
import net.levente.cantotrack.mobile.data.api.WorkType
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.ui.Minutes
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.timeInput
import net.levente.cantotrack.mobile.ui.toUiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A day of the week: its entries, and — from a server that says — what it asks for. */
data class Day(val date: LocalDate, val entries: List<Worklog>, val info: WeekDay? = null) {
    val minutes: Int get() = entries.sumOf { it.minutes }
    val expected: Int? get() = info?.expectedMinutes

    /** A working day gone by with less than it asks for. */
    val short: Boolean get() = info != null && info.expectedMinutes > 0 && minutes < info.expectedMinutes && date.isBefore(LocalDate.now())
}

data class HoursState(
    /** The Monday of the week shown. */
    val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val days: List<Day> = emptyList(),
    /** The week against what it asks for, and where it stands; null from a server without /week. */
    val week: Week? = null,
    val workTypes: List<WorkType> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** An entry being made or changed. */
    val saving: Boolean = false,
    val submitting: Boolean = false,
) {
    val sunday: LocalDate get() = monday.plusDays(6)
    val total: Int get() = days.sumOf { it.minutes }
    val isThisWeek: Boolean get() = !LocalDate.now().isBefore(monday) && !LocalDate.now().isAfter(sunday)
}

/** The fourth tab: one's own hours, a week at a time, day by day, against what the week asks for. */
class HoursViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(HoursState())
    val state: StateFlow<HoursState> = _state.asStateFlow()

    init {
        load()
        loadWorkTypes()
    }

    fun previousWeek() = showWeek(state.value.monday.minusWeeks(1))

    fun nextWeek() = showWeek(state.value.monday.plusWeeks(1))

    fun thisWeek() = showWeek(HoursState().monday)

    /** The week a day picked from the calendar is in. */
    fun showWeekOf(day: LocalDate) = showWeek(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    private fun showWeek(monday: LocalDate) {
        if (monday == state.value.monday) return
        _state.update { it.copy(monday = monday, days = emptyList(), week = null) }
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        val monday = state.value.monday
        _state.update { it.copy(loading = !refreshing && it.days.isEmpty(), refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val entries = async { sessions.call { api.worklogs(it, monday.toString(), monday.plusDays(6).toString()) } }
                    val week = async {
                        try {
                            sessions.call { api.week(it, monday.toString()) }
                        } catch (e: ApiException.Http) {
                            null // An older server: the week is shown without its targets.
                        }
                    }
                    show(monday, entries.await(), week.await())
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    private fun show(monday: LocalDate, entries: List<Worklog>, week: Week?) {
        if (monday != state.value.monday) return
        val byDay = entries.groupBy { LocalDate.parse(it.date) }
        val info = week?.days.orEmpty().associateBy { LocalDate.parse(it.date) }
        val today = LocalDate.now()
        // Every day that has hours or asks for some, and the holidays and days away
        // up to today: an empty Tuesday is worth seeing, an empty Sunday is not.
        val days = (0L..6L).map { monday.plusDays(it) }
            .filter { day ->
                val known = info[day]
                byDay.containsKey(day) || !day.isAfter(today) && (
                    if (known != null) known.expectedMinutes > 0 || known.holiday != null || known.absence != null else day.dayOfWeek.value <= 5
                    )
            }
            .map { day -> Day(day, byDay[day].orEmpty().sortedWith(compareBy(nullsLast()) { it.start }), info[day]) }
            .reversed()
        _state.update { it.copy(days = days, week = week) }
        if (monday == HoursState().monday) {
            container.todayLogged(byDay[today].orEmpty().sumOf { it.minutes })
        }
    }

    private fun loadWorkTypes() {
        viewModelScope.launch {
            try {
                val types = sessions.call { api.workTypes(it) }
                _state.update { it.copy(workTypes = types) }
            } catch (e: ApiException) {
                // Logged without one, as before.
            }
        }
    }

    /**
     * For picking a ticket: what I would log on — starred, logged on lately,
     * in progress — or every ticket matching [query].
     */
    suspend fun searchTickets(query: String): List<Ticket> = sessions.call {
        if (query.isBlank()) {
            try {
                api.suggested(it).ifEmpty { api.tickets(it, "", mineOnly = true).data }
            } catch (e: ApiException.Http) {
                api.tickets(it, "", mineOnly = true).data
            }
        } else {
            api.tickets(it, query, mineOnly = false, openOnly = false).data
        }
    }

    fun log(key: String, minutes: Int, date: String, note: String, workType: String?, onDone: (Boolean) -> Unit) = save(onDone) {
        val entry = sessions.call { api.logWork(it, key, timeInput(minutes), date, note, workType) }
        UiText.Res(R.string.worklog_logged, listOf(Minutes(entry.minutes), key))
    }

    fun update(entry: Worklog, minutes: Int, date: String, note: String, workType: String?, onDone: (Boolean) -> Unit) = save(onDone) {
        val changed = sessions.call { api.updateWorklog(it, entry.id, timeInput(minutes), date, note, workType) }
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
                load(refreshing = false)
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    /** Hands the week in: its hours stop changing until it is approved or sent back. */
    fun submit() {
        if (state.value.submitting) return
        val monday = state.value.monday
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            try {
                val week = sessions.call { api.submitWeek(it, monday.toString()) }
                if (monday == state.value.monday) _state.update { it.copy(week = week, message = UiText.Res(R.string.week_submitted)) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            } finally {
                _state.update { it.copy(submitting = false) }
            }
        }
    }

    fun addAbsence(from: LocalDate, to: LocalDate, kind: String, note: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                sessions.call { api.addAbsence(it, from.toString(), to.toString(), kind, note) }
                _state.update { it.copy(message = UiText.Res(R.string.absence_added)) }
                // The week it starts in, which asks for less now.
                val monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                if (monday == state.value.monday) load(refreshing = true) else showWeek(monday)
                true
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                false
            }
            onDone(ok)
        }
    }

    fun deleteAbsence(absence: Absence) {
        viewModelScope.launch {
            try {
                sessions.call { api.deleteAbsence(it, absence.id) }
                _state.update { it.copy(message = UiText.Res(R.string.absence_deleted)) }
                load(refreshing = true)
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
