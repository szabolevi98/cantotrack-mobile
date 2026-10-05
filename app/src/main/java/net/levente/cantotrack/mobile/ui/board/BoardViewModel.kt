package net.levente.cantotrack.mobile.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Board
import net.levente.cantotrack.mobile.data.api.BoardColumn
import net.levente.cantotrack.mobile.data.api.BoardDetail
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.TicketFilter
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.toUiText

data class BoardState(
    val boards: List<Board>? = null,
    val board: BoardDetail? = null,
    val sprintId: Int? = null,
    val tickets: List<Ticket> = emptyList(),
    val mineOnly: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** The ticket being moved to another column. */
    val moving: String? = null,
    val error: UiText? = null,
    val message: UiText? = null,
) {
    val sprint get() = board?.sprints?.firstOrNull { it.id == sprintId }

    /** The tickets column by column, the way the board shows them; what fits no column last. */
    fun byColumn(): List<Pair<BoardColumn?, List<Ticket>>> {
        val columns = board?.columns.orEmpty()
        val placed = columns.map { column -> column to tickets.filter { it.status.id in column.statusIds } }
        val loose = tickets.filter { t -> columns.none { t.status.id in it.statusIds } }
        return placed + if (loose.isEmpty()) emptyList() else listOf(null to loose)
    }
}

/**
 * The third tab: a board's sprint on a phone — not a board to drag across,
 * but its columns one under the other, with how far the sprint has got.
 * Planning it stays on the web; here a ticket is opened or moved on.
 */
class BoardViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    /** Each project's columns, read when a ticket of it is first moved. */
    private val statuses = HashMap<String, List<Status>>()

    init {
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(loading = !refreshing && it.board == null, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val boards = sessions.call { api.boards(it) }
                val wanted = state.value.board?.id ?: container.prefs.board
                val chosen = boards.firstOrNull { it.id == wanted } ?: likeliest(boards)
                _state.update { it.copy(boards = boards) }
                if (chosen == null) {
                    _state.update { it.copy(board = null, tickets = emptyList()) }
                } else {
                    open(chosen.id, state.value.sprintId)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    /**
     * Before one was ever picked: the own board of the project most of my
     * open tickets are in. A shared board is often a kanban without sprints,
     * so it is only the choice when there is no other.
     */
    private suspend fun likeliest(boards: List<Board>): Board? {
        if (boards.size < 2) return boards.firstOrNull()
        val mine = try {
            sessions.call { api.tickets(it, TicketFilter(mineOnly = true), 1, 100) }.data.groupingBy { it.project }.eachCount()
        } catch (e: ApiException.Http) {
            emptyMap()
        }
        val own = boards.filter { !it.shared }.ifEmpty { boards }
        return own.maxByOrNull { board -> board.projects.sumOf { mine[it] ?: 0 } } ?: boards.first()
    }

    fun selectBoard(id: Int) {
        if (id == state.value.board?.id) return
        container.prefs.board = id
        _state.update { it.copy(loading = true, tickets = emptyList(), sprintId = null, board = null) }
        viewModelScope.launch {
            try {
                open(id, null)
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun selectSprint(id: Int) {
        if (id == state.value.sprintId) return
        _state.update { it.copy(sprintId = id, tickets = emptyList(), loading = true) }
        viewModelScope.launch {
            try {
                loadTickets()
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun setMineOnly(value: Boolean) {
        if (value == state.value.mineOnly) return
        _state.update { it.copy(mineOnly = value) }
        selectSprintAgain()
    }

    private fun selectSprintAgain() {
        viewModelScope.launch {
            try {
                loadTickets()
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            }
        }
    }

    /** The board, and the sprint to show: the one asked for, or the running one, or the next planned. */
    private suspend fun open(id: Int, sprintId: Int?) {
        val board = sessions.call { api.board(it, id) }
        val open = board.sprints.filter { !it.isClosed }
        val sprint = open.firstOrNull { it.id == sprintId } ?: open.firstOrNull { it.isActive } ?: open.firstOrNull()
        _state.update { it.copy(board = board, sprintId = sprint?.id) }
        loadTickets()
    }

    private suspend fun loadTickets() {
        val current = state.value
        val sprint = current.sprintId ?: return _state.update { it.copy(tickets = emptyList()) }
        val filter = TicketFilter(openOnly = false, sprint = sprint, topLevel = true, mineOnly = current.mineOnly)
        val tickets = mutableListOf<Ticket>()
        var page = 1
        do {
            val result = sessions.call { api.tickets(it, filter, page, 100) }
            tickets += result.data
            page++
        } while (page <= result.meta.pages && page <= 5)
        if (state.value.sprintId == sprint) _state.update { it.copy(tickets = tickets) }
    }

    /**
     * Into another column: the status of that column that belongs to the
     * ticket's own project — a shared board's column holds one of each.
     */
    fun move(ticket: Ticket, column: BoardColumn) {
        if (state.value.moving != null || ticket.status.id in column.statusIds) return
        _state.update { it.copy(moving = ticket.key) }
        viewModelScope.launch {
            try {
                val projectStatuses = statuses[ticket.project] ?: sessions.call { api.project(it, ticket.project) }.statuses.also { statuses[ticket.project] = it }
                val status = projectStatuses.firstOrNull { it.id in column.statusIds }
                if (status?.id == null) {
                    _state.update { it.copy(message = UiText.Res(R.string.board_no_such_column, listOf(ticket.key, column.name))) }
                    return@launch
                }
                val moved = sessions.call { api.changeStatus(it, ticket.key, status.id.toString(), ticket.version) }
                _state.update { s ->
                    s.copy(
                        tickets = s.tickets.map { if (it.key == moved.key) moved else it },
                        message = UiText.Res(R.string.board_moved, listOf(moved.key, column.name)),
                    )
                }
            } catch (e: ApiException) {
                if (e is ApiException.Http && e.status == 409) selectSprintAgain()
                _state.update { it.copy(message = e.toUiText()) }
            } finally {
                _state.update { it.copy(moving = null) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
