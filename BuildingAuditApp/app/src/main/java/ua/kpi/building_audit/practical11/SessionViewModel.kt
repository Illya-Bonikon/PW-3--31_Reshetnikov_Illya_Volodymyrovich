package ua.kpi.building_audit.practical11

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.auth.Session
import ua.kpi.building_audit.auth.SessionManager
import ua.kpi.building_audit.auth.SessionState
import ua.kpi.building_audit.data.repository.AuthRepository
import ua.kpi.building_audit.data.repository.TokenProbe
import ua.kpi.building_audit.data.repository.UserProfile
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.ui.containerFactory

data class ProbeResult(val probe: TokenProbe, val text: String, val ok: Boolean)

data class SessionUiState(
    val session: Session? = null,
    val secondsLeft: Long = 0,
    val probes: List<ProbeResult> = emptyList(),
    val users: List<UserProfile> = emptyList()
)

class SessionViewModel(
    private val auth: AuthRepository,
    sessions: SessionManager
) : ViewModel() {
    private val probes = MutableStateFlow<List<ProbeResult>>(emptyList())
    private val users = MutableStateFlow<List<UserProfile>>(emptyList())
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val state: StateFlow<SessionUiState> = combine(sessions.state, ticker, probes, users) { session, now, probeList, userList ->
        val active = (session as? SessionState.Active)?.session
        SessionUiState(active, active?.secondsLeft(now) ?: 0, probeList, userList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    fun probe(mode: TokenProbe) = viewModelScope.launch {
        val result = when (val outcome = auth.probe(mode)) {
            is Outcome.Success -> ProbeResult(mode, "200 OK: ${outcome.value.fullName}", ok = true)
            is Outcome.Failure -> ProbeResult(mode, "${outcome.error.httpCode ?: "-"}: ${outcome.error.userMessage}", ok = false)
        }
        probes.update { list -> list.filterNot { it.probe == mode } + result }
    }

    fun loadUsers() = viewModelScope.launch {
        val outcome = auth.users()
        if (outcome is Outcome.Success) users.value = outcome.value
    }

    fun signOut() = auth.signOut()

    companion object {
        val Factory = containerFactory { SessionViewModel(it.authRepository, it.sessionManager) }
    }
}
