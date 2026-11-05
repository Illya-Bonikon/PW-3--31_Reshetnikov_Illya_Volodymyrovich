package ua.kpi.building_audit.practical12

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.auth.Permission
import ua.kpi.building_audit.auth.SessionManager
import ua.kpi.building_audit.auth.SessionState
import ua.kpi.building_audit.data.repository.ChaosMode
import ua.kpi.building_audit.data.repository.DiagnosticsRepository
import ua.kpi.building_audit.error.AttemptReport
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.error.JournalEntry
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.onSuccess
import ua.kpi.building_audit.network.NetworkState
import ua.kpi.building_audit.ui.containerFactory

data class TestRun(val attempts: List<AttemptReport> = emptyList(), val result: String? = null, val running: Boolean = false)

data class DiagnosticsUiState(
    val network: NetworkState = NetworkState.OFFLINE,
    val chaos: ChaosMode = ChaosMode.NONE,
    val run: TestRun = TestRun(),
    val journal: List<JournalEntry> = emptyList(),
    val canClearJournal: Boolean = false
)

class DiagnosticsViewModel(
    private val repository: DiagnosticsRepository,
    errors: ErrorCenter,
    network: StateFlow<NetworkState>,
    sessions: SessionManager
) : ViewModel() {
    private val chaos = MutableStateFlow(ChaosMode.NONE)
    private val run = MutableStateFlow(TestRun())
    private val journal = MutableStateFlow<List<JournalEntry>>(emptyList())

    val state: StateFlow<DiagnosticsUiState> = combine(network, chaos, run, journal, sessions.state) { net, mode, testRun, entries, session ->
        DiagnosticsUiState(
            network = net,
            chaos = mode,
            run = testRun,
            journal = entries,
            canClearJournal = (session as? SessionState.Active)?.session?.can(Permission.CLEAR_JOURNAL) == true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    init {
        // Кожен новий запис у журналі ErrorCenter одразу оновлює список на екрані.
        viewModelScope.launch { errors.journalVersion.collect { journal.value = repository.journal() } }
    }

    fun setChaos(mode: ChaosMode) = viewModelScope.launch {
        repository.setChaos(mode).onSuccess { chaos.value = mode }
    }

    fun runTest() = viewModelScope.launch {
        run.value = TestRun(running = true)
        val outcome = repository.testRequest { report -> run.update { it.copy(attempts = it.attempts + report) } }
        val text = when (outcome) {
            is Outcome.Success -> "Успіх: заходів у відповіді — ${outcome.value}"
            is Outcome.Failure -> "Відмова: ${outcome.error.userMessage}"
        }
        run.update { it.copy(result = text, running = false) }
    }

    fun clearJournal() = viewModelScope.launch {
        repository.clearJournal()
        journal.value = emptyList()
    }

    companion object {
        val Factory = containerFactory {
            DiagnosticsViewModel(it.diagnosticsRepository, it.errorCenter, it.networkMonitor.state, it.sessionManager)
        }
    }
}
