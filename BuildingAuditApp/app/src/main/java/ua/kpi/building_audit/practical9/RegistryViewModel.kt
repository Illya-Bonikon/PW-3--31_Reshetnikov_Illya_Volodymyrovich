package ua.kpi.building_audit.practical9

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.auth.Permission
import ua.kpi.building_audit.auth.SessionManager
import ua.kpi.building_audit.auth.SessionState
import ua.kpi.building_audit.data.repository.BuildingRepository
import ua.kpi.building_audit.data.repository.DataOrigin
import ua.kpi.building_audit.data.repository.DataSourceMode
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.domain.BuildingDraft
import ua.kpi.building_audit.domain.BuildingQuery
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.PortfolioAggregator
import ua.kpi.building_audit.domain.PortfolioSummary
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.onSuccess
import ua.kpi.building_audit.ui.containerFactory
import java.time.LocalDate

data class RegistryUiState(
    val mode: DataSourceMode = DataSourceMode.AGGREGATED,
    val query: BuildingQuery = BuildingQuery(),
    val items: List<BuildingAssessment> = emptyList(),
    val summary: PortfolioSummary = PortfolioSummary.EMPTY,
    val isLoading: Boolean = false,
    /** Останнє оновлення з мережі не вдалося, на екрані дані з кешу. */
    val isStale: Boolean = false,
    val lastSyncMs: Long? = null,
    val tariffsOrigin: DataOrigin = DataOrigin.FALLBACK,
    val permissions: Set<Permission> = emptySet()
)

private data class LoadStatus(val isLoading: Boolean = false, val isStale: Boolean = false)

/**
 * ViewModel не знає, звідки беруться будівлі: вона лише обирає режим, а репозиторій
 * повертає дані з Room, з мережі або з обох. Розрахунок класів і зведення робить доменний шар.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistryViewModel(
    private val repository: BuildingRepository,
    private val sessions: SessionManager
) : ViewModel() {

    private val mode = MutableStateFlow(DataSourceMode.AGGREGATED)
    private val query = MutableStateFlow(BuildingQuery())
    private val remoteItems = MutableStateFlow<List<Building>>(emptyList())
    private val tariffs = MutableStateFlow(Tariffs.FALLBACK to DataOrigin.FALLBACK)
    private val status = MutableStateFlow(LoadStatus())

    private val buildings = mode.flatMapLatest { if (it == DataSourceMode.REMOTE) remoteItems else repository.observeCached() }

    private val assessed = combine(buildings, tariffs) { list, (tariff, _) -> list.map { EnergyCalculator.assess(it, tariff) } }

    val uiState: StateFlow<RegistryUiState> = combine(
        combine(mode, query, assessed) { m, q, all -> Triple(m, q, all) },
        status,
        repository.observeLastSync(),
        tariffs,
        sessions.state
    ) { (m, q, all), st, lastSync, (_, origin), session ->
        val visible = q.apply(all)
        RegistryUiState(
            mode = m,
            query = q,
            items = visible,
            summary = PortfolioAggregator.summarize(visible),
            isLoading = st.isLoading,
            isStale = st.isStale,
            lastSyncMs = lastSync,
            tariffsOrigin = origin,
            permissions = (session as? SessionState.Active)?.session?.role?.permissions.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegistryUiState())

    init {
        reload()
    }

    fun selectMode(newMode: DataSourceMode) {
        mode.value = newMode
        reload()
    }

    fun updateQuery(newQuery: BuildingQuery) {
        query.value = newQuery
    }

    fun reload(force: Boolean = false) = viewModelScope.launch {
        status.update { it.copy(isLoading = true) }
        if (mode.value != DataSourceMode.LOCAL) {
            val fetched = repository.tariffs()
            tariffs.value = fetched.value to fetched.origin
        }
        val failed = when (mode.value) {
            DataSourceMode.LOCAL -> false
            DataSourceMode.REMOTE -> repository.fetchRemote().onSuccess { remoteItems.value = it } is Outcome.Failure
            DataSourceMode.AGGREGATED -> repository.refreshCache(force) is Outcome.Failure
        }
        status.value = LoadStatus(isLoading = false, isStale = failed)
    }

    fun add(draft: BuildingDraft) = viewModelScope.launch {
        repository.save(draft.toBuilding(LocalDate.now().toString()), mode.value)
            .onSuccess { if (mode.value == DataSourceMode.REMOTE) reload() }
    }

    fun delete(building: Building) = viewModelScope.launch {
        repository.delete(building, mode.value)
            .onSuccess { if (mode.value == DataSourceMode.REMOTE) reload() }
    }

    companion object {
        val Factory = containerFactory { RegistryViewModel(it.buildingRepository, it.sessionManager) }
    }
}
