package de.erikedits.pocketbot.data

import android.net.Uri
import android.os.Build
import de.erikedits.pocketbot.shared.api.ApiException
import de.erikedits.pocketbot.shared.api.ApiUser
import de.erikedits.pocketbot.shared.api.ExchangeRequest
import de.erikedits.pocketbot.shared.api.PocketBotApi
import de.erikedits.pocketbot.shared.util.Pkce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

sealed interface AuthState {
    data object LoggedOut : AuthState
    data object Working : AuthState
    data class LoggedIn(val user: ApiUser, val devMode: Boolean = false) : AuthState
    /** [code] is the backend/Discord error code, e.g. access_denied, backend_offline, network. */
    data class Failed(val code: String) : AuthState
}

/** "Mit Discord anmelden" via Eriks bot (docs/BACKEND-INTEGRATION.md 4.2). */
class AuthRepository(
    private val api: PocketBotApi,
    private val secure: SecureStore,
    private val prefs: Prefs,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun initialState(): AuthState {
        if (secure.get(KEY_SESSION) == null) return AuthState.LoggedOut
        val user = prefs.userJson?.let { runCatching { json.decodeFromString(ApiUser.serializer(), it) }.getOrNull() }
        return if (user != null) AuthState.LoggedIn(user) else AuthState.LoggedOut
    }

    /** Returns the URL to open in a Custom Tab. */
    fun beginLogin(): String {
        val state = Pkce.randomUrlSafe(16)
        val verifier = Pkce.newVerifier()
        secure.put(KEY_PENDING_STATE, state)
        secure.put(KEY_PENDING_VERIFIER, verifier)
        _state.value = AuthState.Working
        return api.loginUrl(state, Pkce.challenge(verifier))
    }

    @Volatile private var redirectInFlight = false

    /** The Custom Tab was closed without coming back (user pressed back). */
    fun cancelPending() {
        if (!redirectInFlight && _state.value == AuthState.Working) _state.value = AuthState.LoggedOut
    }

    suspend fun handleRedirect(uri: Uri) {
        redirectInFlight = true
        try {
            handleRedirectInner(uri)
        } finally {
            redirectInFlight = false
        }
    }

    private suspend fun handleRedirectInner(uri: Uri) {
        val expected = secure.get(KEY_PENDING_STATE)
        val verifier = secure.get(KEY_PENDING_VERIFIER)
        val state = uri.getQueryParameter("state")
        if (expected == null || verifier == null || state != expected) {
            _state.value = AuthState.Failed("state_mismatch")
            return
        }
        secure.put(KEY_PENDING_STATE, null)
        secure.put(KEY_PENDING_VERIFIER, null)
        uri.getQueryParameter("error")?.let {
            _state.value = AuthState.Failed(it)
            return
        }
        val code = uri.getQueryParameter("login_code") ?: run {
            _state.value = AuthState.Failed("oauth_failed")
            return
        }
        _state.value = AuthState.Working
        try {
            val result = api.exchange(ExchangeRequest(code, verifier, deviceName()))
            secure.put(KEY_SESSION, result.sessionToken)
            prefs.userJson = json.encodeToString(ApiUser.serializer(), result.user)
            _state.value = AuthState.LoggedIn(result.user)
        } catch (e: ApiException) {
            _state.value = AuthState.Failed(e.code)
        }
    }

    /** Refreshes the profile; a 401 logs out locally. Other errors keep the cached user. */
    suspend fun refresh() {
        val current = _state.value as? AuthState.LoggedIn ?: return
        if (current.devMode) return
        try {
            val user = api.me()
            prefs.userJson = json.encodeToString(ApiUser.serializer(), user)
            _state.value = AuthState.LoggedIn(user)
        } catch (e: ApiException) {
            if (e.isUnauthorized) clearLocal()
        }
    }

    suspend fun logout() {
        val current = _state.value
        if (current is AuthState.LoggedIn && !current.devMode) runCatching { api.logout() }
        clearLocal()
    }

    /** Debug builds only: test the app before the backend exists. */
    fun devLogin() {
        _state.value = AuthState.LoggedIn(ApiUser(id = "0", username = "dev", globalName = "Dev-Modus"), devMode = true)
    }

    fun dismissError() {
        if (_state.value is AuthState.Failed) _state.value = AuthState.LoggedOut
    }

    private fun clearLocal() {
        secure.put(KEY_SESSION, null)
        prefs.userJson = null
        _state.value = AuthState.LoggedOut
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(64)

    companion object {
        const val KEY_SESSION = "session_token"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
    }
}
