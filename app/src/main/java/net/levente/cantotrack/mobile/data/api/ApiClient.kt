package net.levente.cantotrack.mobile.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Where to send requests, and as whom. */
data class Connection(val baseUrl: String, val token: String)

/**
 * The CantoTrack JSON API, as far as the app needs it (docs/API.md in the
 * cantotrack repository). Every call runs on the IO dispatcher and throws
 * [ApiException] on failure.
 */
class ApiClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val resendKeys: ResendKeys = ResendKeys(),
) {

    // -----------------------------------------------------------------
    // Signing in and out
    // -----------------------------------------------------------------

    /**
     * [code] is the two-step sign-in code, for a person who has it turned on:
     * without it the server answers 403 with [ApiException.Http.twoFactorRequired].
     */
    suspend fun login(baseUrl: String, email: String, password: String, device: String, code: String? = null): LoginResult {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("device", device)
            if (code != null) put("code", code)
        }
        val request = Request.Builder().url(url(baseUrl, "auth/login", null)).post(body.toBody()).build()
        return execute(request, Envelope.serializer(LoginResult.serializer()), unauthorizedIsHttp = true).data
    }

    suspend fun me(connection: Connection): User = get(connection, "me", emptyMap(), Envelope.serializer(User.serializer())).data

    suspend fun logout(connection: Connection) {
        send(authorized(connection, "auth/logout", emptyMap()).post(EMPTY).build())
    }

    // -----------------------------------------------------------------
    // Tickets
    // -----------------------------------------------------------------

    /**
     * Tickets, newest change first: the person's own, or everybody's with
     * [mineOnly] off; only the open ones unless [openOnly] is off.
     */
    suspend fun tickets(connection: Connection, search: String, mineOnly: Boolean, page: Int = 1, openOnly: Boolean = true): Page<Ticket> =
        tickets(connection, TicketFilter(search = search, mineOnly = mineOnly, openOnly = openOnly), page)

    suspend fun tickets(connection: Connection, filter: TicketFilter, page: Int = 1, perPage: Int = 50): Page<Ticket> =
        get(connection, "tickets", filter.toQuery(page, perPage), Page.serializer(Ticket.serializer()))

    suspend fun ticket(connection: Connection, key: String): Ticket =
        get(connection, "tickets/$key", emptyMap(), Envelope.serializer(Ticket.serializer())).data

    /** project, title, and whatever else is set: type, description, priority, assignee_id, due_on. */
    suspend fun createTicket(connection: Connection, fields: JsonObject): Ticket {
        val request = authorized(connection, "tickets", emptyMap()).post(fields.toBody()).build()
        return execute(request, Envelope.serializer(Ticket.serializer())).data
    }

    /**
     * Changes the [fields] sent and no others. [version] is the one the ticket
     * was read at: somebody else's change in between answers 409.
     */
    suspend fun updateTicket(connection: Connection, key: String, fields: JsonObject, version: Int?): Ticket {
        val body = JsonObject(if (version == null) fields else fields + ("version" to JsonPrimitive(version)))
        val request = authorized(connection, "tickets/$key", emptyMap()).patch(body.toBody()).build()
        return execute(request, Envelope.serializer(Ticket.serializer())).data
    }

    /** Moves a ticket to the column [status], by its name or its id. */
    suspend fun changeStatus(connection: Connection, key: String, status: String, version: Int): Ticket =
        updateTicket(connection, key, buildJsonObject { put("status", status) }, version)

    /** The projects I can see; the archived ones are left out. */
    suspend fun projects(connection: Connection): List<Project> =
        get(connection, "projects", emptyMap(), Envelope.serializer(ListSerializer(Project.serializer()))).data

    /** The project's columns, for changing a ticket's status. */
    suspend fun project(connection: Connection, code: String): Project =
        get(connection, "projects/$code", emptyMap(), Envelope.serializer(Project.serializer())).data

    /** The active people: who a ticket can be given to. */
    suspend fun users(connection: Connection): List<Person> =
        get(connection, "users", emptyMap(), Envelope.serializer(ListSerializer(Person.serializer()))).data

    suspend fun star(connection: Connection, key: String, starred: Boolean) {
        val builder = authorized(connection, "tickets/$key/star", emptyMap())
        send((if (starred) builder.post(EMPTY_JSON) else builder.delete()).build())
    }

    suspend fun watch(connection: Connection, key: String, watching: Boolean) {
        val builder = authorized(connection, "tickets/$key/watch", emptyMap())
        send((if (watching) builder.post(EMPTY_JSON) else builder.delete()).build())
    }

    /** My starred tickets that are not finished. */
    suspend fun starred(connection: Connection): List<Ticket> = ticketList(connection, "starred")

    /** The tickets I opened lately, on the web or here. */
    suspend fun recent(connection: Connection): List<Ticket> = ticketList(connection, "recent")

    /** What to log time on: starred, logged on lately, in progress. */
    suspend fun suggested(connection: Connection): List<Ticket> = ticketList(connection, "tickets/suggested")

    private suspend fun ticketList(connection: Connection, path: String): List<Ticket> =
        get(connection, path, emptyMap(), Envelope.serializer(ListSerializer(Ticket.serializer()))).data

    suspend fun links(connection: Connection, key: String): List<TicketLink> =
        get(connection, "tickets/$key/links", emptyMap(), Envelope.serializer(ListSerializer(TicketLink.serializer()))).data

    // -----------------------------------------------------------------
    // Comments
    // -----------------------------------------------------------------

    suspend fun comments(connection: Connection, key: String): List<Comment> =
        get(connection, "tickets/$key/comments", emptyMap(), Envelope.serializer(ListSerializer(Comment.serializer()))).data

    suspend fun addComment(connection: Connection, key: String, text: String): Comment {
        val request = authorized(connection, "tickets/$key/comments", emptyMap())
            .post(buildJsonObject { put("body", text) }.toBody())
            .build()
        return execute(request, Envelope.serializer(Comment.serializer())).data
    }

    /** Corrects a comment of one's own. */
    suspend fun updateComment(connection: Connection, id: Int, text: String): Comment {
        val request = authorized(connection, "comments/$id", emptyMap())
            .patch(buildJsonObject { put("body", text) }.toBody())
            .build()
        return execute(request, Envelope.serializer(Comment.serializer())).data
    }

    suspend fun deleteComment(connection: Connection, id: Int) {
        send(authorized(connection, "comments/$id", emptyMap()).delete().build())
    }

    // -----------------------------------------------------------------
    // Attachments
    // -----------------------------------------------------------------

    suspend fun attachments(connection: Connection, key: String): List<Attachment> =
        get(connection, "tickets/$key/attachments", emptyMap(), Envelope.serializer(ListSerializer(Attachment.serializer()))).data

    /**
     * One file onto a ticket. The multipart boundary comes from the content,
     * so the same file sent again after a lost answer is the same request —
     * and goes with the same Idempotency-Key.
     */
    suspend fun upload(connection: Connection, key: String, name: String, type: String, bytes: ByteArray): Uploaded {
        val boundary = "ct-" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(40)
        val body = MultipartBody.Builder(boundary)
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(type.toMediaTypeOrNull()))
            .build()
        return execute(authorized(connection, "tickets/$key/attachments", emptyMap()).post(body).build(), Uploaded.serializer())
    }

    /** The file itself, written to [target]. */
    suspend fun download(connection: Connection, id: Int, target: File): Unit = withContext(Dispatchers.IO) {
        val request = authorized(connection, "attachments/$id", emptyMap()).get().build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }
        response.use {
            if (!it.isSuccessful) {
                val error = parseError(it.body.string()) ?: throw ApiException.BadResponse("HTTP ${it.code}")
                if (it.code == 401) throw ApiException.Unauthorized(error.second)
                throw ApiException.Http(it.code, error.second, error.third)
            }
            val partial = File(target.path + ".part")
            try {
                partial.outputStream().use { out -> it.body.byteStream().copyTo(out) }
            } catch (e: IOException) {
                partial.delete()
                throw ApiException.Network(e)
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw ApiException.Network(IOException("could not keep the file"))
            }
        }
    }

    suspend fun deleteAttachment(connection: Connection, id: Int) {
        send(authorized(connection, "attachments/$id", emptyMap()).delete().build())
    }

    // -----------------------------------------------------------------
    // Hours
    // -----------------------------------------------------------------

    /** [time] as the web takes it: "1h 30m". [date] is 2026-09-22. [workType] by its name, or none. */
    suspend fun logWork(connection: Connection, key: String, time: String, date: String, note: String, workType: String? = null): Worklog {
        val body = buildJsonObject {
            put("time", time)
            put("date", date)
            if (note.isNotBlank()) put("note", note.trim())
            if (!workType.isNullOrBlank()) put("work_type", workType)
        }
        val request = authorized(connection, "tickets/$key/worklogs", emptyMap()).post(body.toBody()).build()
        return execute(request, Envelope.serializer(Worklog.serializer())).data
    }

    /** The person's own hours from [from] to [to], both 2026-09-22. */
    suspend fun worklogs(connection: Connection, from: String, to: String): List<Worklog> =
        get(connection, "worklogs", mapOf("from" to from, "to" to to), Envelope.serializer(ListSerializer(Worklog.serializer()))).data

    /** A ticket's hours, everybody's, the newest day first. */
    suspend fun ticketWorklogs(connection: Connection, key: String): List<Worklog> =
        get(connection, "tickets/$key/worklogs", emptyMap(), Envelope.serializer(ListSerializer(Worklog.serializer()))).data

    /**
     * Changes an entry of one's own; the start and billing stay as they were,
     * and so does the work type unless [workType] is given.
     */
    suspend fun updateWorklog(connection: Connection, id: Int, time: String, date: String, note: String, workType: String? = null): Worklog {
        val body = buildJsonObject {
            put("time", time)
            put("date", date)
            put("note", note.trim())
            if (!workType.isNullOrBlank()) put("work_type", workType)
        }
        val request = authorized(connection, "worklogs/$id", emptyMap()).patch(body.toBody()).build()
        return execute(request, Envelope.serializer(Worklog.serializer())).data
    }

    suspend fun deleteWorklog(connection: Connection, id: Int) {
        send(authorized(connection, "worklogs/$id", emptyMap()).delete().build())
    }

    /** What hours can be logged as. */
    suspend fun workTypes(connection: Connection): List<WorkType> =
        get(connection, "work-types", emptyMap(), Envelope.serializer(ListSerializer(WorkType.serializer()))).data

    /** The week [day] is in, against what each of its days asks for. */
    suspend fun week(connection: Connection, day: String): Week =
        get(connection, "week", mapOf("week" to day), Envelope.serializer(Week.serializer())).data

    /** Hands the week [day] is in over for approval. */
    suspend fun submitWeek(connection: Connection, day: String): Week {
        val request = authorized(connection, "week/submit", emptyMap()).post(buildJsonObject { put("week", day) }.toBody()).build()
        return execute(request, Envelope.serializer(Week.serializer())).data
    }

    /** Days away: [kind] is vacation, sick or other. */
    suspend fun addAbsence(connection: Connection, from: String, to: String, kind: String, note: String): Absence {
        val body = buildJsonObject {
            put("starts_on", from)
            put("ends_on", to)
            put("kind", kind)
            if (note.isNotBlank()) put("note", note.trim())
        }
        val request = authorized(connection, "absences", emptyMap()).post(body.toBody()).build()
        return execute(request, Envelope.serializer(Absence.serializer())).data
    }

    suspend fun deleteAbsence(connection: Connection, id: Int) {
        send(authorized(connection, "absences/$id", emptyMap()).delete().build())
    }

    // -----------------------------------------------------------------
    // Planning
    // -----------------------------------------------------------------

    suspend fun boards(connection: Connection): List<Board> =
        get(connection, "boards", emptyMap(), Envelope.serializer(ListSerializer(Board.serializer()))).data

    /** One board, with its columns and its sprints, the running one first. */
    suspend fun board(connection: Connection, id: Int): BoardDetail =
        get(connection, "boards/$id", emptyMap(), Envelope.serializer(BoardDetail.serializer())).data

    // -----------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------

    suspend fun notifications(connection: Connection, page: Int = 1, unreadOnly: Boolean = false, perPage: Int = 30): NotificationPage {
        val query = buildMap {
            if (unreadOnly) put("unread", "1")
            put("page", "$page")
            put("per_page", "$perPage")
        }
        return get(connection, "notifications", query, NotificationPage.serializer())
    }

    suspend fun readNotification(connection: Connection, id: Int) {
        send(authorized(connection, "notifications/$id/read", emptyMap()).post(EMPTY_JSON).build())
    }

    suspend fun readAllNotifications(connection: Connection) {
        send(authorized(connection, "notifications/read", emptyMap()).post(EMPTY_JSON).build())
    }

    // -----------------------------------------------------------------
    // The clock
    // -----------------------------------------------------------------

    suspend fun timer(connection: Connection): Timer? =
        get(connection, "timer", emptyMap(), Envelope.serializer(Timer.serializer().nullable)).data

    /** Starts the clock on a ticket; one running on another ticket is logged first. */
    suspend fun startTimer(connection: Connection, key: String): TimerStarted =
        execute(authorized(connection, "tickets/$key/timer", emptyMap()).post(EMPTY_JSON).build(), TimerStarted.serializer())

    /** Stops the clock and logs it. Null when it ran under a minute, and nothing was logged. */
    suspend fun stopTimer(connection: Connection, note: String?): LoggedTime? {
        val body = buildJsonObject { if (!note.isNullOrBlank()) put("note", note.trim()) }
        val request = authorized(connection, "timer/stop", emptyMap()).post(body.toBody()).build()
        return execute(request, Envelope.serializer(LoggedTime.serializer().nullable)).data
    }

    suspend fun discardTimer(connection: Connection) {
        send(authorized(connection, "timer", emptyMap()).delete().build())
    }

    // -----------------------------------------------------------------
    // The request
    // -----------------------------------------------------------------

    private suspend fun <T> get(connection: Connection, path: String, query: Map<String, String>, serializer: KSerializer<T>): T =
        execute(authorized(connection, path, query).get().build(), serializer)

    private fun authorized(connection: Connection, path: String, query: Map<String, String>): Request.Builder =
        Request.Builder()
            .url(url(connection.baseUrl, path, query))
            .header("Authorization", "Bearer ${connection.token}")

    private suspend fun <T> execute(request: Request, serializer: KSerializer<T>, unauthorizedIsHttp: Boolean = false): T {
        val text = send(request, unauthorizedIsHttp)
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: SerializationException) {
            throw ApiException.BadResponse(e.message ?: "unexpected response")
        } catch (e: IllegalArgumentException) {
            throw ApiException.BadResponse(e.message ?: "unexpected response")
        }
    }

    /**
     * Sends the request and returns the body of a successful answer, which may be empty (204).
     *
     * A change — anything but a GET, and but signing in and out — carries an
     * Idempotency-Key (see [ResendKeys]), so that sending it again after a lost
     * connection is safe.
     */
    private suspend fun send(request: Request, unauthorizedIsHttp: Boolean = false): String = withContext(Dispatchers.IO) {
        val fingerprint = if (request.method != "GET" && !request.url.encodedPath.contains("/api/v1/auth/")) fingerprintOf(request) else null
        val sent = request.newBuilder().header("Accept", "application/json")
        if (fingerprint != null) sent.header("Idempotency-Key", resendKeys.keyFor(fingerprint))

        val response = try {
            http.newCall(sent.build()).execute()
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }

        response.use {
            val text = try {
                it.body.string()
            } catch (e: IOException) {
                throw ApiException.Network(e)
            }

            // Answered: the key is done with — unless the first sending is still being worked on.
            if (fingerprint != null && it.code != 409) resendKeys.answered(fingerprint)

            if (!it.isSuccessful) {
                val error = parseError(text) ?: throw ApiException.BadResponse("HTTP ${it.code}")
                if (it.code == 401 && !unauthorizedIsHttp) {
                    throw ApiException.Unauthorized(error.second)
                }
                throw ApiException.Http(it.code, error.second, error.third)
            }
            text
        }
    }

    /** What makes two sendings the same change: the method, the address and the body. */
    private fun fingerprintOf(request: Request): String {
        val body = Buffer()
        request.body?.writeTo(body)
        return request.method + " " + request.url + "\n" + body.readUtf8()
    }

    /** {"error": {"status", "message", "details"?}} as (status, message, details), or null. */
    private fun parseError(text: String): Triple<Int, String, JsonElement?>? = try {
        val error = json.parseToJsonElement(text).jsonObject["error"]?.jsonObject ?: return null
        Triple(
            error["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            error["message"]?.jsonPrimitive?.contentOrNull ?: "",
            error["details"],
        )
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun JsonObject.toBody(): RequestBody = toString().toRequestBody(JSON)

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY = ByteArray(0).toRequestBody(null)
        private val EMPTY_JSON = "{}".toRequestBody(JSON)

        /**
         * The server address as typed by a person: "tracker.example.com" gets
         * https://, and a trailing slash or a pasted "/api/v1" is dropped.
         * Returns null when it is not a usable http(s) address.
         */
        fun normalizeBaseUrl(input: String): String? {
            var value = input.trim()
            if (value.isEmpty()) return null
            if (!value.contains("://")) value = "https://$value"
            value = value.trimEnd('/').removeSuffix("/v1").removeSuffix("/api").trimEnd('/')
            val parsed = value.toHttpUrlOrNull() ?: return null
            if (parsed.scheme != "http" && parsed.scheme != "https") return null
            return value
        }

        internal fun url(baseUrl: String, path: String, query: Map<String, String>?): HttpUrl {
            val builder = ("$baseUrl/api/v1/$path").toHttpUrlOrNull()?.newBuilder()
                ?: throw ApiException.BadResponse("invalid server address")
            query?.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            return builder.build()
        }
    }
}

