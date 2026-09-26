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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Where to send requests, and as whom. */
data class Connection(val baseUrl: String, val token: String)

/**
 * The CantoTrack JSON API, as far as the app needs it (the API section of
 * cantotrack's README). Every call runs on the IO dispatcher and throws
 * [ApiException] on failure.
 */
class ApiClient(private val http: OkHttpClient, private val json: Json) {

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

    /** Open tickets, newest change first: the person's own, or everybody's with [mineOnly] off. */
    suspend fun tickets(connection: Connection, search: String, mineOnly: Boolean, page: Int = 1): Page<Ticket> {
        val query = buildMap {
            put("open", "1")
            if (mineOnly) put("assignee", "me")
            if (search.isNotBlank()) put("q", search.trim())
            put("page", "$page")
            put("per_page", "50")
        }
        return get(connection, "tickets", query, Page.serializer(Ticket.serializer()))
    }

    suspend fun ticket(connection: Connection, key: String): Ticket =
        get(connection, "tickets/$key", emptyMap(), Envelope.serializer(Ticket.serializer())).data

    /** The project's columns, for changing a ticket's status. */
    suspend fun project(connection: Connection, code: String): Project =
        get(connection, "projects/$code", emptyMap(), Envelope.serializer(Project.serializer())).data

    /**
     * Moves a ticket to the column named [status]. [version] is the one the
     * ticket was read at: somebody else's change in between answers 409.
     */
    suspend fun changeStatus(connection: Connection, key: String, status: String, version: Int): Ticket {
        val body = buildJsonObject {
            put("status", status)
            put("version", version)
        }
        val request = authorized(connection, "tickets/$key", emptyMap()).patch(body.toBody()).build()
        return execute(request, Envelope.serializer(Ticket.serializer())).data
    }

    suspend fun comments(connection: Connection, key: String): List<Comment> =
        get(connection, "tickets/$key/comments", emptyMap(), Envelope.serializer(ListSerializer(Comment.serializer()))).data

    suspend fun addComment(connection: Connection, key: String, text: String): Comment {
        val request = authorized(connection, "tickets/$key/comments", emptyMap())
            .post(buildJsonObject { put("body", text) }.toBody())
            .build()
        return execute(request, Envelope.serializer(Comment.serializer())).data
    }

    // -----------------------------------------------------------------
    // Hours
    // -----------------------------------------------------------------

    /** [time] as the web takes it: "1h 30m". [date] is 2026-09-22. */
    suspend fun logWork(connection: Connection, key: String, time: String, date: String, note: String): Worklog {
        val body = buildJsonObject {
            put("time", time)
            put("date", date)
            if (note.isNotBlank()) put("note", note.trim())
        }
        val request = authorized(connection, "tickets/$key/worklogs", emptyMap()).post(body.toBody()).build()
        return execute(request, Envelope.serializer(Worklog.serializer())).data
    }

    /** The person's own hours from [from] to [to], both 2026-09-22. */
    suspend fun worklogs(connection: Connection, from: String, to: String): List<Worklog> =
        get(connection, "worklogs", mapOf("from" to from, "to" to to), Envelope.serializer(ListSerializer(Worklog.serializer()))).data

    suspend fun deleteWorklog(connection: Connection, id: Int) {
        send(authorized(connection, "worklogs/$id", emptyMap()).delete().build())
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

    /** Sends the request and returns the body of a successful answer, which may be empty (204). */
    private suspend fun send(request: Request, unauthorizedIsHttp: Boolean = false): String = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request.newBuilder().header("Accept", "application/json").build()).execute()
        } catch (e: IOException) {
            throw ApiException.Network(e)
        }

        response.use {
            val text = try {
                it.body.string()
            } catch (e: IOException) {
                throw ApiException.Network(e)
            }

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

