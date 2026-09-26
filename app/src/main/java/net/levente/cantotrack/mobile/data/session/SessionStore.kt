package net.levente.cantotrack.mobile.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.levente.cantotrack.mobile.data.api.Connection
import net.levente.cantotrack.mobile.data.api.User

/** A signed-in person on a server. */
data class Session(val baseUrl: String, val token: String, val user: User) {
    fun connection() = Connection(baseUrl, token)
}

/** Keeps the session between app starts; the token is stored encrypted. */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    suspend fun load(): Session? {
        val prefs = dataStore.data.first()
        val baseUrl = prefs[BASE_URL] ?: return null
        val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return null
        val user = prefs[USER]?.let { runCatching { json.decodeFromString(User.serializer(), it) }.getOrNull() } ?: return null
        return Session(baseUrl, token, user)
    }

    suspend fun save(session: Session) {
        dataStore.edit {
            it[BASE_URL] = session.baseUrl
            it[LAST_BASE_URL] = session.baseUrl
            it[LAST_EMAIL] = session.user.email
            it[TOKEN] = cipher.encrypt(session.token)
            it[USER] = json.encodeToString(User.serializer(), session.user)
        }
    }

    /** Signs out on the device; the server address and email stay for the next sign-in. */
    suspend fun clear() {
        dataStore.edit {
            it.remove(BASE_URL)
            it.remove(TOKEN)
            it.remove(USER)
        }
    }

    suspend fun lastBaseUrl(): String? = dataStore.data.first()[LAST_BASE_URL]

    suspend fun lastEmail(): String? = dataStore.data.first()[LAST_EMAIL]

    private companion object {
        val BASE_URL = stringPreferencesKey("session_base_url")
        val TOKEN = stringPreferencesKey("session_token")
        val USER = stringPreferencesKey("session_user")
        val LAST_BASE_URL = stringPreferencesKey("last_base_url")
        val LAST_EMAIL = stringPreferencesKey("last_email")
    }
}
