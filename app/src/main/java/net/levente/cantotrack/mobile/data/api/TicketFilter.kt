package net.levente.cantotrack.mobile.data.api

/**
 * What GET /tickets is asked for. Every filter is optional, and they add up
 * on the server, the way the web's ticket list combines them.
 */
data class TicketFilter(
    /** Words in the title, the text or the comments — or a key. */
    val search: String = "",
    /** Only the ones assigned to me. */
    val mineOnly: Boolean = false,
    /** Only the unfinished ones. */
    val openOnly: Boolean = true,
    /** overdue, or week: due in the next seven days. */
    val due: String? = null,
    /** task, bug or story. */
    val type: String? = null,
    /** A project's code. */
    val project: String? = null,
    /** The ticket list's query language: project = CT AND priority = high. */
    val query: String? = null,
    val sprint: Int? = null,
    /** A ticket's key: its subtasks. */
    val parent: String? = null,
    /** Only tickets, not their subtasks. */
    val topLevel: Boolean = false,
) {
    fun toQuery(page: Int, perPage: Int): Map<String, String> = buildMap {
        if (openOnly) put("open", "1")
        if (mineOnly) put("assignee", "me")
        if (search.isNotBlank()) put("q", search.trim())
        due?.let { put("due", it) }
        type?.let { put("type", it) }
        project?.let { put("project", it) }
        query?.takeIf { it.isNotBlank() }?.let { put("query", it.trim()) }
        sprint?.let { put("sprint", "$it") }
        parent?.let { put("parent", it) }
        if (topLevel) put("top_level", "1")
        put("page", "$page")
        put("per_page", "$perPage")
    }
}
