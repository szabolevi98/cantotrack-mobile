package net.levente.cantotrack.mobile.ui.ticket

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Attachment
import net.levente.cantotrack.mobile.data.api.Comment
import net.levente.cantotrack.mobile.data.api.Person
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.data.api.Ticket
import net.levente.cantotrack.mobile.data.api.TicketFilter
import net.levente.cantotrack.mobile.data.api.TicketLink
import net.levente.cantotrack.mobile.data.api.WorkType
import net.levente.cantotrack.mobile.data.api.Worklog
import net.levente.cantotrack.mobile.data.files.Images
import net.levente.cantotrack.mobile.ui.Minutes
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.timeInput
import net.levente.cantotrack.mobile.ui.toUiText
import java.io.IOException

/** A sprint a ticket can be put in: one not closed, on a board its project is on. */
data class SprintOption(val id: Int, val name: String, val board: String, val active: Boolean)

data class TicketState(
    val ticket: Ticket? = null,
    val comments: List<Comment> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val links: List<TicketLink> = emptyList(),
    val subtasks: List<Ticket> = emptyList(),
    val worklogs: List<Worklog> = emptyList(),
    /** The project's columns, read when the status is first tapped. */
    val statuses: List<Status> = emptyList(),
    /** Read when they are first needed. */
    val people: List<Person>? = null,
    val sprints: List<SprintOption>? = null,
    val workTypes: List<WorkType> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiText? = null,
    val sendingComment: Boolean = false,
    val logging: Boolean = false,
    val changingStatus: Boolean = false,
    /** A field being saved. */
    val saving: Boolean = false,
    val uploading: Boolean = false,
    /** The file being downloaded to be opened. */
    val opening: Int? = null,
    /** Said once in the snackbar. */
    val message: UiText? = null,
)

class TicketViewModel(
    val key: String,
    private val container: AppContainer,
) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(TicketState())
    val state: StateFlow<TicketState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load(refreshing = true)

    /**
     * The ticket and its comments first; its files, links, subtasks and hours
     * beside them. One of those that fails leaves the rest of the page.
     */
    fun load(refreshing: Boolean = false) {
        _state.update { it.copy(loading = !refreshing && it.ticket == null, refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val ticket = async { sessions.call { api.ticket(it, key) } }
                    val comments = async { sessions.call { api.comments(it, key) } }
                    val attachments = async { quietly { sessions.call { api.attachments(it, key) } } }
                    val links = async { quietly { sessions.call { api.links(it, key) } } }
                    val worklogs = async { quietly { sessions.call { api.ticketWorklogs(it, key) } } }
                    val read = ticket.await()
                    val subtasks = if (read.subtasks.count > 0) {
                        quietly { sessions.call { api.tickets(it, TicketFilter(openOnly = false, parent = key), 1, 100).data } }
                    } else {
                        emptyList()
                    }
                    _state.update {
                        it.copy(
                            ticket = read,
                            comments = comments.await(),
                            attachments = attachments.await() ?: it.attachments,
                            links = links.await() ?: it.links,
                            worklogs = worklogs.await() ?: it.worklogs,
                            subtasks = subtasks ?: it.subtasks,
                        )
                    }
                }
                // Opening the ticket read what the bell said about it, on the server.
                runCatching { sessions.call { container.news.refresh(it) } }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: ApiException.Unauthorized) {
        throw e
    } catch (e: ApiException) {
        null
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

    fun loadPeople() {
        if (state.value.people != null) return
        viewModelScope.launch {
            try {
                val people = sessions.call { api.users(it) }
                _state.update { it.copy(people = people) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    /** The sprints the ticket can go into: on the boards its project is on, not closed. */
    fun loadSprints() {
        val ticket = state.value.ticket ?: return
        if (state.value.sprints != null) return
        viewModelScope.launch {
            try {
                val boards = sessions.call { api.boards(it) }.filter { ticket.project in it.projects }
                val options = boards.flatMap { board ->
                    sessions.call { api.board(it, board.id) }.sprints.filter { !it.isClosed }.map { SprintOption(it.id, it.name, board.name, it.isActive) }
                }
                _state.update { it.copy(sprints = options) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText(), sprints = emptyList()) }
            }
        }
    }

    fun loadWorkTypes() {
        if (state.value.workTypes.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val types = sessions.call { api.workTypes(it) }
                _state.update { it.copy(workTypes = types) }
            } catch (e: ApiException) {
                // Logged without one, as before.
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
                _state.update { it.copy(ticket = changed.keepMine(it.ticket), message = UiText.Res(R.string.ticket_status_changed, listOf(changed.status.name))) }
            } catch (e: ApiException) {
                if (e is ApiException.Http && e.status == 409) load(refreshing = true)
                _state.update { it.copy(message = e.toUiText()) }
            } finally {
                _state.update { it.copy(changingStatus = false) }
            }
        }
    }

    fun setAssignee(person: Person?) = change(R.string.ticket_saved) { put("assignee_id", person?.id) }

    /** "Take it": given to me, with one tap. */
    fun takeIt(me: Person) = change(R.string.ticket_taken) { put("assignee_id", me.id) }

    fun setPriority(priority: String) = change(R.string.ticket_saved) { put("priority", priority) }

    fun setDue(day: String?) = change(R.string.ticket_saved) { put("due_on", day) }

    fun setSprint(sprint: SprintOption?) = change(R.string.ticket_saved) {
        if (sprint == null) put("sprint", JsonNull) else put("sprint", sprint.id)
    }

    fun setText(title: String, description: String, onDone: (Boolean) -> Unit) = change(R.string.ticket_saved, onDone) {
        put("title", title.trim())
        put("description", description.trim())
    }

    /** Only the fields given change, and only if nobody else saved it since it was read. */
    private fun change(said: Int, onDone: (Boolean) -> Unit = {}, fields: JsonObjectBuilder.() -> Unit) {
        val ticket = state.value.ticket ?: return
        if (state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = try {
                val changed = sessions.call { api.updateTicket(it, key, buildJsonObject(fields), ticket.version) }
                _state.update { it.copy(ticket = changed.keepMine(it.ticket), message = UiText.Res(said)) }
                true
            } catch (e: ApiException) {
                if (e is ApiException.Http && e.status == 409) load(refreshing = true)
                _state.update { it.copy(message = e.toUiText()) }
                false
            } finally {
                _state.update { it.copy(saving = false) }
            }
            onDone(ok)
        }
    }

    fun toggleStar() {
        val ticket = state.value.ticket ?: return
        val starred = ticket.starred != true
        _state.update { it.copy(ticket = ticket.copy(starred = starred)) }
        viewModelScope.launch {
            try {
                sessions.call { api.star(it, key, starred) }
                _state.update { it.copy(message = UiText.Res(if (starred) R.string.ticket_starred else R.string.ticket_unstarred)) }
            } catch (e: ApiException) {
                _state.update { it.copy(ticket = it.ticket?.copy(starred = !starred), message = e.toUiText()) }
            }
        }
    }

    fun toggleWatch() {
        val ticket = state.value.ticket ?: return
        val watching = ticket.watching != true
        _state.update { it.copy(ticket = ticket.copy(watching = watching)) }
        viewModelScope.launch {
            try {
                sessions.call { api.watch(it, key, watching) }
                _state.update { it.copy(message = UiText.Res(if (watching) R.string.ticket_watching else R.string.ticket_not_watching)) }
            } catch (e: ApiException) {
                _state.update { it.copy(ticket = it.ticket?.copy(watching = !watching), message = e.toUiText()) }
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

    fun editComment(comment: Comment, text: String, onDone: (Boolean) -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val ok = try {
                val edited = sessions.call { api.updateComment(it, comment.id, text.trim()) }
                _state.update { s -> s.copy(comments = s.comments.map { if (it.id == edited.id) edited else it }, message = UiText.Res(R.string.comment_saved)) }
                true
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                false
            }
            onDone(ok)
        }
    }

    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            try {
                sessions.call { api.deleteComment(it, comment.id) }
                _state.update { s -> s.copy(comments = s.comments.filter { it.id != comment.id }, message = UiText.Res(R.string.comment_deleted)) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    /** A photo or file onto the ticket: pictures made smaller first. */
    fun upload(uri: Uri) {
        if (state.value.uploading) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            try {
                val file = Images.prepare(container.context, uri)
                val result = sessions.call { api.upload(it, key, file.name, file.type, file.bytes) }
                _state.update { s ->
                    s.copy(
                        attachments = s.attachments + result.data,
                        message = result.errors.firstOrNull()?.let(UiText::Raw) ?: UiText.Res(R.string.attachment_uploaded, listOf(file.name)),
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            } catch (e: IOException) {
                _state.update { it.copy(message = UiText.Res(R.string.attachment_unreadable)) }
            } catch (e: SecurityException) {
                _state.update { it.copy(message = UiText.Res(R.string.attachment_unreadable)) }
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    /** Downloads it once, and opens it in whatever app the phone has for it. */
    fun open(attachment: Attachment) {
        if (state.value.opening != null) return
        _state.update { it.copy(opening = attachment.id) }
        viewModelScope.launch {
            try {
                val file = sessions.call { container.files.file(it, attachment) }
                if (!container.files.open(file, attachment.type)) {
                    _state.update { it.copy(message = UiText.Res(R.string.attachment_no_app)) }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            } finally {
                _state.update { it.copy(opening = null) }
            }
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        viewModelScope.launch {
            try {
                sessions.call { api.deleteAttachment(it, attachment.id) }
                _state.update { s -> s.copy(attachments = s.attachments.filter { it.id != attachment.id }, message = UiText.Res(R.string.attachment_deleted)) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
            }
        }
    }

    suspend fun thumbnail(attachment: Attachment) = try {
        sessions.call { container.files.thumbnail(it, attachment, 200) }
    } catch (e: ApiException) {
        null
    }

    fun logWork(minutes: Int, date: String, note: String, workType: String?, onDone: (Boolean) -> Unit) {
        if (state.value.logging) return
        _state.update { it.copy(logging = true) }
        viewModelScope.launch {
            val logged = try {
                val entry = sessions.call { api.logWork(it, key, timeInput(minutes), date, note, workType) }
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

    fun messageFrom(text: String) = _state.update { it.copy(message = UiText.Raw(text)) }

    /** A ticket from a change keeps what only a read says: the star and following. */
    private fun Ticket.keepMine(before: Ticket?): Ticket =
        copy(starred = starred ?: before?.starred, watching = watching ?: before?.watching)
}
