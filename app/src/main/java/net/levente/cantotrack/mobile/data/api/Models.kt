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

@Serializable
data class Ticket(
    val key: String,
    val project: String,
    val type: String = "task",
    val title: String,
    val status: Status,
    val resolution: String? = null,
    val priority: String = "medium",
    val assignee: Person? = null,
    val labels: List<String> = emptyList(),
    val sprint: String? = null,
    val epic: EpicRef? = null,
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
    val statuses: List<Status> = emptyList(),
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
