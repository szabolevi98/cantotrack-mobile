package net.levente.cantotrack.mobile.ui.newticket

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Person
import net.levente.cantotrack.mobile.data.api.Project
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.data.files.Images
import net.levente.cantotrack.mobile.data.files.PreparedFile
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.toUiText
import java.io.IOException

/** A photo or file waiting to go up with the ticket, and its small picture. */
class PendingFile(val file: PreparedFile, val preview: ImageBitmap?) {
    val id = System.nanoTime()
}

data class NewTicketState(
    val projects: List<Project>? = null,
    val project: String? = null,
    val type: String = "task",
    val title: String = "",
    val description: String = "",
    val priority: String = "normal",
    val assignee: Person? = null,
    val people: List<Person>? = null,
    val dueOn: String? = null,
    val files: List<PendingFile> = emptyList(),
    val preparing: Boolean = false,
    val saving: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** Made: the key to open. */
    val created: String? = null,
) {
    val canSave: Boolean get() = project != null && title.isNotBlank() && !saving && !preparing
}

/**
 * A new ticket from the phone: what was found on the way, with the photo of
 * it. Only what a phone is good for — the rest can be filled in on the web.
 */
class NewTicketViewModel(private val container: AppContainer, me: User) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(NewTicketState(assignee = Person(me.id, me.name)))
    val state: StateFlow<NewTicketState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val projects = sessions.call { api.projects(it) }.filter { !it.archived }
                val last = container.prefs.lastProject
                _state.update { s -> s.copy(projects = projects, project = s.project ?: projects.firstOrNull { it.code == last }?.code ?: projects.firstOrNull()?.code) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText(), projects = emptyList()) }
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

    fun update(block: (NewTicketState) -> NewTicketState) = _state.update(block)

    fun addFile(uri: Uri) {
        _state.update { it.copy(preparing = true) }
        viewModelScope.launch {
            try {
                val file = Images.prepare(container.context, uri)
                val preview = if (file.type.startsWith("image/")) withContext(Dispatchers.Default) { preview(file.bytes) } else null
                _state.update { it.copy(files = it.files + PendingFile(file, preview)) }
            } catch (e: IOException) {
                _state.update { it.copy(message = UiText.Res(R.string.attachment_unreadable)) }
            } catch (e: SecurityException) {
                _state.update { it.copy(message = UiText.Res(R.string.attachment_unreadable)) }
            } finally {
                _state.update { it.copy(preparing = false) }
            }
        }
    }

    fun removeFile(file: PendingFile) = _state.update { it.copy(files = it.files - file) }

    fun save() {
        val s = state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val fields = buildJsonObject {
                    put("project", s.project)
                    put("title", s.title.trim())
                    put("type", s.type)
                    put("priority", s.priority)
                    if (s.description.isNotBlank()) put("description", s.description.trim())
                    s.assignee?.let { put("assignee_id", it.id) }
                    s.dueOn?.let { put("due_on", it) }
                }
                val ticket = sessions.call { api.createTicket(it, fields) }
                container.prefs.lastProject = s.project

                // The ticket is there; a file that does not get up is said, not lost silently.
                var failed = 0
                for (pending in s.files) {
                    try {
                        val result = sessions.call { api.upload(it, ticket.key, pending.file.name, pending.file.type, pending.file.bytes) }
                        failed += result.errors.size
                    } catch (e: ApiException) {
                        failed++
                    }
                }
                _state.update {
                    it.copy(
                        created = ticket.key,
                        message = if (failed > 0) UiText.Res(R.string.new_ticket_files_failed, listOf(failed)) else UiText.Res(R.string.new_ticket_created, listOf(ticket.key)),
                    )
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun preview(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 240) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}
