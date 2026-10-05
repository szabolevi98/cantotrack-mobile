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
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.data.SavedQuery
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Project
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.TicketFilter
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.toUiText
import java.time.LocalDate

/** Which tickets the list shows: a filtered page, or one of my own short lists. */
enum class TicketView { LIST, STARRED, RECENT }

data class TicketsState(
    val tickets: List<Ticket> = emptyList(),
    val search: String = "",
    /** Only the ones assigned to me; off, it is every open ticket I can see. */
    val mineOnly: Boolean = true,
    val view: TicketView = TicketView.LIST,
    /** overdue, or week. */
    val due: String? = null,
    val bugsOnly: Boolean = false,
    val project: String? = null,
    val query: SavedQuery? = null,
    val projects: List<Project>? = null,
    val savedQueries: List<SavedQuery> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    val todayMinutes: Int? = null,
    val error: UiText? = null,
) {
    val hasMore: Boolean get() = view == TicketView.LIST && page < pages

    /** Anything narrowing the list beyond mine-or-all and the search. */
    val filtered: Boolean get() = view != TicketView.LIST || due != null || bugsOnly || project != null || query != null

    fun filter() = TicketFilter(
        search = search,
        mineOnly = mineOnly,
        due = due,
        type = if (bugsOnly) "bug" else null,
        project = project,
        query = query?.query,
    )
}

/** The first tab: open tickets, mine by default, with a search and a few quick filters. */
class TicketsViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(TicketsState(savedQueries = container.prefs.savedQueries))
    val state: StateFlow<TicketsState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

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

    fun setMineOnly(value: Boolean) = change { it.copy(mineOnly = value, view = TicketView.LIST) }

    /** Starred or recent; the same one again goes back to the list. */
    fun setView(view: TicketView) = change { it.copy(view = if (it.view == view) TicketView.LIST else view) }

    fun setDue(due: String?) = change { it.copy(due = if (it.due == due) null else due, view = TicketView.LIST) }

    fun toggleBugs() = change { it.copy(bugsOnly = !it.bugsOnly, view = TicketView.LIST) }

    fun setProject(code: String?) = change { it.copy(project = code, view = TicketView.LIST) }

    fun setQuery(query: SavedQuery?) = change { it.copy(query = query, view = TicketView.LIST) }

    /** Keeps [query] under its name, and shows what it finds. */
    fun saveQuery(query: SavedQuery) {
        val saved = container.prefs.savedQueries.filter { it.name != query.name } + query
        container.prefs.savedQueries = saved
        _state.update { it.copy(savedQueries = saved) }
        setQuery(query)
    }

    fun forgetQuery(query: SavedQuery) {
        val saved = container.prefs.savedQueries.filter { it != query }
        container.prefs.savedQueries = saved
        _state.update { it.copy(savedQueries = saved, query = if (it.query == query) null else it.query) }
        if (state.value.query == null) load()
    }

    /** Every filter off: my open tickets again. */
    fun clearFilters() = change { it.copy(view = TicketView.LIST, due = null, bugsOnly = false, project = null, query = null) }

    private fun change(block: (TicketsState) -> TicketsState) {
        _state.update { block(it).copy(tickets = emptyList()) }
        load()
    }

    /** The projects, for the project filter — read once, when it is first opened. */
    fun loadProjects() {
        if (state.value.projects != null) return
        viewModelScope.launch {
            try {
                val projects = sessions.call { api.projects(it) }.filter { !it.archived }
                _state.update { it.copy(projects = projects) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            }
        }
    }

    fun refresh() = load(refreshing = true)

    /** Everything again: the list from its first page, and today's hours. */
    fun load(refreshing: Boolean = false) {
        val current = state.value
        _state.update { it.copy(loading = !refreshing && it.tickets.isEmpty(), refreshing = refreshing, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                when (current.view) {
                    TicketView.LIST -> {
                        val page = sessions.call { api.tickets(it, current.filter()) }
                        _state.update { it.copy(tickets = page.data, page = page.meta.page, pages = page.meta.pages, total = page.meta.total) }
                    }
                    else -> {
                        val all = sessions.call { if (current.view == TicketView.STARRED) api.starred(it) else api.recent(it) }
                        val words = current.search.trim()
                        val shown = all.filter { words.isEmpty() || it.title.contains(words, true) || it.key.contains(words, true) }
                        _state.update { it.copy(tickets = shown, page = 1, pages = 1, total = shown.size) }
                    }
                }
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
                val page = sessions.call { api.tickets(it, current.filter(), current.page + 1) }
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
            minutes?.let(container::todayLogged)
            _state.update { it.copy(todayMinutes = minutes ?: it.todayMinutes) }
        }
    }
}
