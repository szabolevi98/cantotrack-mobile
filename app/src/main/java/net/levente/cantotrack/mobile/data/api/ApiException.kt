package net.levente.cantotrack.mobile.data.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Everything that can go wrong talking to the server, in the shape the UI needs. */
sealed class ApiException(message: String) : Exception(message) {

    /** The request may or may not have reached the server (timeout, lost connection). */
    class Network(cause: Throwable) : ApiException(cause.message ?: "network error")

    /** The token is no longer valid: the person has to sign in again. */
    class Unauthorized(message: String) : ApiException(message)

    /**
     * The server answered with an error. Its [message] is a whole sentence in
     * the person's language, written for them — CantoTrack says why.
     */
    class Http(val status: Int, message: String, val details: JsonElement?) : ApiException(message) {

        /** Two-step sign-in is on: the server wants the code from the person's app. */
        val twoFactorRequired: Boolean
            get() = (details as? JsonObject)?.get("two_factor_required")?.jsonPrimitive?.booleanOrNull == true
    }

    /** The server sent something that is not the CantoTrack API (wrong address, a proxy's page). */
    class BadResponse(message: String) : ApiException(message)
}
