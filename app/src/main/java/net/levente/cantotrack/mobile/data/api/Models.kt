package net.levente.cantotrack.mobile.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who is signed in, as GET /api/v1/me tells it. */
@Serializable
data class User(
    val id: Int,
    val name: String,
    val handle: String? = null,
    val email: String = "",
    /** admin, member or guest. Guests read and comment, but log no time. */
    val role: String = "member",
) {
    val isGuest: Boolean get() = role == "guest"
    val isAdmin: Boolean get() = role == "admin"

    /** The initials of the avatar: "Anna Kovács" is AK. */
    val initials: String
        get() = name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@Serializable
data class LoginResult(val token: String, val user: User)

@Serializable
data class Person(val id: Int, val name: String)

@Serializable
data class Status(
    val id: Int? = null,
    val name: String,
    /** todo, in_progress or done: what the colour of the status follows. */
    val category: String,
)

@Serializable
data class EpicRef(val id: Int? = null, val title: String)

/** How many of something, and how many of them are done. */
@Serializable
data class Progress(val count: Int = 0, val done: Int = 0)

@Serializable
data class Ticket(
    val key: String,
    val id: Int = 0,
    val project: String,
    val type: String = "task",
    val title: String,
    val status: Status,
    val resolution: String? = null,
    val priority: String = "normal",
    val assignee: Person? = null,
    val labels: List<String> = emptyList(),
    val sprint: String? = null,
    val epic: EpicRef? = null,
    /** The key of the ticket this is a subtask of. */
    val parent: String? = null,
    val release: String? = null,
    val subtasks: Progress = Progress(),
    @SerialName("story_points") val storyPoints: Int? = null,
    @SerialName("estimate_minutes") val estimateMinutes: Int? = null,
    @SerialName("logged_minutes") val loggedMinutes: Int = 0,
    @SerialName("remaining_minutes") val remainingMinutes: Int? = null,
    @SerialName("due_on") val dueOn: String? = null,
    val version: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    val url: String? = null,
    /** Only on a single ticket: Markdown, as it was written. */
    val description: String? = null,
    val reporter: Person? = null,
    /** Only on a single ticket, from a server that says: whether I starred it. */
    val starred: Boolean? = null,
    /** Only on a single ticket: whether its changes reach me. */
    val watching: Boolean? = null,
) {
    val isDone: Boolean get() = status.category == "done"
}

@Serializable
data class Comment(
    val id: Int,
    val author: Person,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
)

@Serializable
data class Worklog(
    val id: Int,
    /** The ticket's key, CT-12. */
    val ticket: String,
    val user: Person,
    /** 2026-09-22 */
    val date: String,
    /** 09:30, or null when only the hours were given. */
    val start: String? = null,
    val minutes: Int,
    @SerialName("work_type") val workType: String? = null,
    val note: String? = null,
    val billable: Boolean = true,
)

/** The clock that runs for the person: the same one the web shows. */
@Serializable
data class Timer(
    val ticket: String,
    val title: String,
    @SerialName("started_at") val startedAt: String,
    /** How long it has run, by the server's clock, when it was read. */
    val seconds: Long,
)

@Serializable
data class LoggedTime(val minutes: Int, val ticket: String? = null)

@Serializable
data class TimerStarted(val data: Timer?, val logged: LoggedTime? = null)

@Serializable
data class Project(
    val code: String,
    val name: String,
    val archived: Boolean = false,
    val statuses: List<Status> = emptyList(),
)

@Serializable
data class Attachment(
    val id: Int,
    val name: String,
    /** Its media type, as the server found it by its content. */
    val type: String = "application/octet-stream",
    val size: Long = 0,
    val image: ImageSize? = null,
    val author: AttachmentAuthor? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isImage: Boolean get() = image != null || type.startsWith("image/")
}

@Serializable
data class ImageSize(val width: Int? = null, val height: Int? = null)

@Serializable
data class AttachmentAuthor(val id: Int, val name: String? = null)

/** The ones that got in, and why the others did not. */
@Serializable
data class Uploaded(val data: List<Attachment> = emptyList(), val errors: List<String> = emptyList())

@Serializable
data class LinkStatus(val name: String, val category: String)

/** A link, read from the ticket it was asked about. */
@Serializable
data class TicketLink(
    val id: Int,
    /** blocks, blocked_by, relates, duplicates or duplicated_by. */
    val kind: String,
    val ticket: String,
    val title: String,
    val status: LinkStatus,
)

@Serializable
data class NotificationTicket(val key: String, val title: String)

@Serializable
data class Notification(
    val id: Int,
    /** assigned, mentioned, status, commented or changes. */
    val kind: String,
    /** Why it reached me: assigned, mentioned or watching. */
    val reason: String = "watching",
    val read: Boolean = false,
    /** The bell's sentence, in my own language. */
    val text: String,
    val actor: Person? = null,
    val ticket: NotificationTicket? = null,
    val epic: EpicRef? = null,
    val project: String? = null,
    @SerialName("created_at") val createdAt: String,
    val url: String? = null,
)

@Serializable
data class NotificationMeta(
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
    /** How many are unread in all: the number on the bell. */
    val unread: Int = 0,
)

@Serializable
data class NotificationPage(val data: List<Notification>, val meta: NotificationMeta)

@Serializable
data class Board(
    val id: Int,
    val name: String,
    val shared: Boolean = false,
    val projects: List<String> = emptyList(),
)

@Serializable
data class BoardColumn(
    val name: String,
    val done: Boolean = false,
    @SerialName("status_ids") val statusIds: List<Int> = emptyList(),
)

@Serializable
data class Points(val total: Int = 0, val done: Int = 0)

@Serializable
data class Sprint(
    val id: Int,
    val name: String,
    val goal: String? = null,
    /** planned, active or closed. */
    val state: String,
    @SerialName("starts_on") val startsOn: String? = null,
    @SerialName("ends_on") val endsOn: String? = null,
    val tickets: Progress? = null,
    val points: Points? = null,
) {
    val isActive: Boolean get() = state == "active"
    val isClosed: Boolean get() = state == "closed"
}

@Serializable
data class BoardDetail(
    val id: Int,
    val name: String,
    val shared: Boolean = false,
    val projects: List<String> = emptyList(),
    val columns: List<BoardColumn> = emptyList(),
    val sprints: List<Sprint> = emptyList(),
)

@Serializable
data class WorkType(val id: Int, val name: String)

@Serializable
data class WeekDay(
    val date: String,
    /** What the day asks for: the person's own week, 0 on a holiday or a day away. */
    @SerialName("expected_minutes") val expectedMinutes: Int = 0,
    @SerialName("logged_minutes") val loggedMinutes: Int = 0,
    val holiday: String? = null,
    /** vacation, sick or other. */
    val absence: String? = null,
)

@Serializable
data class WeekState(
    /** submitted, approved or rejected (sent back). */
    val state: String,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    val reviewer: Person? = null,
    /** Why it was sent back. */
    val comment: String? = null,
)

@Serializable
data class Absence(
    val id: Int,
    @SerialName("starts_on") val startsOn: String,
    @SerialName("ends_on") val endsOn: String,
    val kind: String = "vacation",
    val note: String? = null,
)

/** A week the way the timesheet reads it. */
@Serializable
data class Week(
    val monday: String,
    val sunday: String,
    val days: List<WeekDay> = emptyList(),
    @SerialName("logged_minutes") val loggedMinutes: Int = 0,
    @SerialName("expected_minutes") val expectedMinutes: Int = 0,
    /** Up to today: the days still to come are not missing yet. */
    @SerialName("expected_to_date_minutes") val expectedToDateMinutes: Int = 0,
    val state: WeekState? = null,
    @SerialName("can_submit") val canSubmit: Boolean = false,
    @SerialName("locked_until") val lockedUntil: String? = null,
    val absences: List<Absence> = emptyList(),
)

@Serializable
data class Envelope<T>(val data: T)

@Serializable
data class Page<T>(val data: List<T>, val meta: PageMeta)

@Serializable
data class PageMeta(
    val page: Int,
    @SerialName("per_page") val perPage: Int = 50,
    val total: Int = 0,
    val pages: Int = 1,
)
