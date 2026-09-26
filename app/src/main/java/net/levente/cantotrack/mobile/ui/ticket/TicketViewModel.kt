package net.levente.cantotrack.mobile.ui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Comment
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.session.SessionManager
import net.levente.cantotrack.mobile.ui.Minutes
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.timeInput
import net.levente.cantotrack.mobile.ui.toUiText

data class TicketState(
    val ticket: Ticket? = null,
    val comments: List<Comment> = emptyList(),
    /** The project's columns, read when the status is first tapped. */
    val statuses: List<Status> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val sendingComment: Boolean = false,
    val logging: Boolean = false,
    val changingStatus: Boolean = false,
    /** Said once in the snackbar. */
    val message: UiText? = null,
)

class TicketViewModel(
    val key: String,
    private val api: ApiClient,
    private val sessions: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(TicketState())
    val state: StateFlow<TicketState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(loading = !refreshing && it.ticket == null, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val ticket = sessions.call { api.ticket(it, key) }
                val comments = sessions.call { api.comments(it, key) }
                _state.update { it.copy(ticket = ticket, comments = comments) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun loadStatuses() {
        val ticket = state.value.ticket ?: return
        if (state.value.statuses.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val project = sessions.call { api.project(it, ticket.project) }
                _state.update { it.copy(statuses = project.statuses) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    /** A move the workflow does not allow, or a change somebody made meanwhile, is said as the server says it. */
    fun changeStatus(status: Status) {
        val ticket = state.value.ticket ?: return
        if (status.name == ticket.status.name) return
        _state.update { it.copy(changingStatus = true) }
        viewModelScope.launch {
            try {
                val changed = sessions.call { api.changeStatus(it, key, status.name, ticket.version) }
                _state.update { it.copy(ticket = changed, message = UiText.Res(R.string.ticket_status_changed, listOf(changed.status.name))) }
            } catch (e: ApiException) {
                if (e is ApiException.Http && e.status == 409) load(refreshing = true)
                _state.update { it.copy(message = e.toUiText()) }
            } finally {
                _state.update { it.copy(changingStatus = false) }
            }
        }
    }

    /** Returns through [onDone] whether it was posted, so the field can be emptied. */
    fun addComment(text: String, onDone: (Boolean) -> Unit) {
        if (text.isBlank() || state.value.sendingComment) return
        _state.update { it.copy(sendingComment = true) }
        viewModelScope.launch {
            val posted = try {
                val comment = sessions.call { api.addComment(it, key, text.trim()) }
                _state.update { it.copy(comments = it.comments + comment) }
                true
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                false
            } finally {
                _state.update { it.copy(sendingComment = false) }
            }
            onDone(posted)
        }
    }

    fun logWork(minutes: Int, date: String, note: String, onDone: (Boolean) -> Unit) {
        if (state.value.logging) return
        _state.update { it.copy(logging = true) }
        viewModelScope.launch {
            val logged = try {
                val entry = sessions.call { api.logWork(it, key, timeInput(minutes), date, note) }
                _state.update { it.copy(message = UiText.Res(R.string.worklog_logged, listOf(Minutes(entry.minutes), key))) }
                refresh()
                true
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                false
            } finally {
                _state.update { it.copy(logging = false) }
            }
            onDone(logged)
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
