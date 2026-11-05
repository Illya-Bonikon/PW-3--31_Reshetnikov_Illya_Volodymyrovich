package ua.kpi.building_audit.practical10

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.kpi.building_audit.data.remote.toDto
import ua.kpi.building_audit.data.repository.BuildingRepository
import ua.kpi.building_audit.data.repository.DataOrigin
import ua.kpi.building_audit.data.repository.Fetched
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.SavingsForecast
import ua.kpi.building_audit.domain.SavingsForecaster
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.ui.containerFactory

/** Рядок журналу викликів: який ендпоінт, скільки тривав і чим закінчився. */
data class EndpointCall(val endpoint: String, val durationMs: Long, val result: String, val ok: Boolean)

data class BuildingDetails(
    val assessment: BuildingAssessment,
    val forecasts: List<SavingsForecast>,
    val origin: DataOrigin,
    val json: String
)

data class ApiExplorerUiState(
    val calls: List<EndpointCall> = emptyList(),
    val buildings: List<Building> = emptyList(),
    val tariffs: Tariffs? = null,
    val selectedId: Long? = null,
    val details: BuildingDetails? = null,
    val isLoading: Boolean = false,
    val showJson: Boolean = false
)

class ApiExplorerViewModel(private val repository: BuildingRepository) : ViewModel() {
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val _state = MutableStateFlow(ApiExplorerUiState())
    val state: StateFlow<ApiExplorerUiState> = _state.asStateFlow()

    init {
        loadAll()
    }

    /** Список і тарифи — незалежні ресурси, тому запити йдуть паралельно. */
    fun loadAll() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, calls = emptyList()) }
        val list = async { timed("GET /buildings") { repository.fetchRemote() } }
        val tariffs = async { timed("GET /tariffs") { Outcome.Success(repository.tariffs()) } }

        val tariffsResult = (tariffs.await() as Outcome.Success).value
        _state.update { it.copy(tariffs = tariffsResult.value) }
        val listResult = list.await()
        if (listResult is Outcome.Success) {
            _state.update { it.copy(buildings = listResult.value) }
            listResult.value.firstOrNull()?.let { select(it.id) }
        }
        _state.update { it.copy(isLoading = false) }
    }

    fun select(id: Long) = viewModelScope.launch {
        _state.update { it.copy(selectedId = id) }
        val tariffs = state.value.tariffs ?: Tariffs.FALLBACK
        val result = timed("GET /buildings/$id") { repository.details(id) }
        if (result is Outcome.Success) {
            val building = result.value.value
            val details = BuildingDetails(
                assessment = EnergyCalculator.assess(building, tariffs),
                forecasts = SavingsForecaster.forecastAll(building, tariffs),
                origin = result.value.origin,
                json = prettyGson.toJson(building.toDto())
            )
            _state.update { it.copy(details = details) }
        }
    }

    fun toggleJson() = _state.update { it.copy(showJson = !it.showJson) }

    private suspend fun <T> timed(endpoint: String, call: suspend () -> Outcome<T>): Outcome<T> {
        val started = System.currentTimeMillis()
        val result = call()
        val summary = when (result) {
            is Outcome.Success -> when (val value = result.value) {
                is List<*> -> "${value.size} об'єктів"
                is Fetched<*> -> "джерело: ${value.origin.title}"
                else -> "успіх"
            }
            is Outcome.Failure -> result.error.userMessage
        }
        val entry = EndpointCall(endpoint, System.currentTimeMillis() - started, summary, result is Outcome.Success)
        _state.update { it.copy(calls = it.calls + entry) }
        return result
    }

    companion object {
        val Factory = containerFactory { ApiExplorerViewModel(it.buildingRepository) }
    }
}
