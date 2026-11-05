package ua.kpi.building_audit.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.kpi.building_audit.error.AuthFailure

interface TokenStore {
    fun read(): String?
    fun save(token: String)
    fun clear()
}

/**
 * SharedPreferences замість DataStore: AuthInterceptor читає токен синхронно на потоці OkHttp,
 * а DataStore вимагав би runBlocking усередині інтерцептора.
 */
class PrefsTokenStore(context: Context) : TokenStore {
    private val prefs = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)
    override fun save(token: String) = prefs.edit().putString(KEY, token).apply()
    override fun clear() = prefs.edit().remove(KEY).apply()

    private companion object {
        const val KEY = "access_token"
    }
}

sealed interface SessionState {
    data object SignedOut : SessionState
    data class Active(val session: Session) : SessionState
    /** Сеанс завершено не з волі користувача; екран входу показує причину. */
    data class Ended(val login: String, val reason: AuthFailure) : SessionState
}

class SessionManager(
    private val store: TokenStore,
    private val decoder: JwtDecoder,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val currentToken: String? get() = (state.value as? SessionState.Active)?.session?.token

    fun start(token: String): Session {
        val session = decoder.decode(token)
        store.save(token)
        _state.value = SessionState.Active(session)
        return session
    }

    fun signOut() {
        store.clear()
        _state.value = SessionState.SignedOut
    }

    fun end(reason: AuthFailure) {
        val active = state.value as? SessionState.Active ?: return
        store.clear()
        _state.value = SessionState.Ended(active.session.login, reason)
    }

    /** Викликається щосекунди: прострочений токен завершує сеанс ще до відмови сервера. */
    fun checkExpiry() {
        val active = state.value as? SessionState.Active ?: return
        if (active.session.isExpired(clock())) end(AuthFailure.TOKEN_EXPIRED)
    }

    private fun restore(): SessionState {
        val token = store.read() ?: return SessionState.SignedOut
        val session = runCatching { decoder.decode(token) }.getOrNull()
        if (session == null || session.isExpired(clock())) {
            store.clear()
            return SessionState.SignedOut
        }
        return SessionState.Active(session)
    }
}
