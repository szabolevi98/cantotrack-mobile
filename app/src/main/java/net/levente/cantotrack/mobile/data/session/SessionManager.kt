package net.levente.cantotrack.mobile.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.data.api.ApiClient
import net.levente.cantotrack.mobile.data.api.ApiException

sealed interface SessionState {
    data object Loading : SessionState

    /** [expired] is true when the server refused the token, so the sign-in screen can say why. */
    data class SignedOut(val expired: Boolean = false) : SessionState

    data class SignedIn(val session: Session) : SessionState
}

/** The one place that knows whether somebody is signed in; every screen follows [state]. */
class SessionManager(
    private val store: SessionStore,
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val current: Session? get() = (state.value as? SessionState.SignedIn)?.session

    /**
     * Restores the stored session and checks the token in the background. Only
     * a definite 401 signs out: without a network the person keeps working with
     * what the app has, and the next request says so.
     */
    fun restore() {
        scope.launch {
            val session = store.load()
            if (session == null) {
                _state.value = SessionState.SignedOut()
                return@launch
            }
            _state.value = SessionState.SignedIn(session)
            refresh(session)
        }
    }

    private suspend fun refresh(session: Session) {
        try {
            val user = api.me(session.connection())
            if (user != session.user) {
                val updated = session.copy(user = user)
                store.save(updated)
                _state.value = SessionState.SignedIn(updated)
            }
        } catch (e: ApiException.Unauthorized) {
            expire()
        } catch (e: ApiException) {
            // Offline or server trouble: keep the stored session.
        }
    }

    /** Throws [ApiException] when the sign-in fails; [code] is the two-step sign-in code, when it is on. */
    suspend fun signIn(baseUrl: String, email: String, password: String, device: String, code: String? = null): Session {
        val result = api.login(baseUrl, email, password, device, code)
        val session = Session(baseUrl, result.token, result.user)
        store.save(session)
        _state.value = SessionState.SignedIn(session)
        return session
    }

    /** Revokes the token on the server when it can, and forgets it on the device either way. */
    fun signOut() {
        val session = current
        _state.value = SessionState.SignedOut()
        scope.launch {
            store.clear()
            if (session != null) {
                try {
                    api.logout(session.connection())
                } catch (e: ApiException) {
                    // It stays on the profile's list of tokens, where it can be revoked by hand.
                }
            }
        }
    }

    /** The server no longer takes the token: a new password, signed out elsewhere, deactivated. */
    fun expire() {
        if (state.value is SessionState.SignedOut) return
        _state.value = SessionState.SignedOut(expired = true)
        scope.launch { store.clear() }
    }

    suspend fun lastBaseUrl(): String? = store.lastBaseUrl()

    suspend fun lastEmail(): String? = store.lastEmail()
}
