package net.levente.cantotrack.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.session.SessionManager
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.toUiText
import java.time.LocalDate

data class TicketsState(
    val tickets: List<Ticket> = emptyList(),
    val search: String = "",
    /** Only the ones assigned to me; off, it is every open ticket I can see. */
    val mineOnly: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val todayMinutes: Int? = null,
    val error: UiText? = null,
) {
    val hasMore: Boolean get() = page < pages
}

/** The first tab: open tickets, mine by default, with a search over their titles and keys. */
class TicketsViewModel(private val api: ApiClient, private val sessions: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(TicketsState())
    val state: StateFlow<TicketsState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun setSearch(value: String) {
        _state.update { it.copy(search = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            load()
        }
    }

    fun setMineOnly(value: Boolean) {
        if (value == state.value.mineOnly) return
        _state.update { it.copy(mineOnly = value, tickets = emptyList()) }
        load()
    }

    fun refresh() = load(refreshing = true)

    /** Everything again: the list from its first page, and today's hours. */
    fun load(refreshing: Boolean = false) {
        val current = state.value
        _state.update { it.copy(loading = !refreshing && it.tickets.isEmpty(), refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val page = sessions.call { api.tickets(it, current.search, current.mineOnly) }
                _state.update { it.copy(tickets = page.data, page = page.meta.page, pages = page.meta.pages, total = page.meta.total) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
            loadToday()
        }
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasMore || current.loadingMore || current.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = sessions.call { api.tickets(it, current.search, current.mineOnly, current.page + 1) }
                _state.update { state ->
                    val known = state.tickets.map { it.key }.toSet()
                    state.copy(tickets = state.tickets + page.data.filter { it.key !in known }, page = page.meta.page, pages = page.meta.pages)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** The day's hours, after something was logged. */
    fun loadToday() {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val minutes = try {
                sessions.call { api.worklogs(it, today, today) }.sumOf { it.minutes }
            } catch (e: ApiException) {
                null
            }
            _state.update { it.copy(todayMinutes = minutes ?: it.todayMinutes) }
        }
    }
}
