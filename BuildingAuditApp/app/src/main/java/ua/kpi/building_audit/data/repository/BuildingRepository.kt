package ua.kpi.building_audit.data.repository

import kotlinx.coroutines.flow.Flow
import ua.kpi.building_audit.data.local.LocalBuildingDataSource
import ua.kpi.building_audit.data.remote.RemoteBuildingDataSource
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs
import ua.kpi.building_audit.error.AppError
import ua.kpi.building_audit.error.ErrorCenter
import ua.kpi.building_audit.error.Outcome
import ua.kpi.building_audit.error.RetryPolicy

enum class DataSourceMode(val title: String) {
    LOCAL("Локально"),
    REMOTE("Сервер"),
    AGGREGATED("Агреговано")
}

enum class DataOrigin(val title: String) {
    NETWORK("сервер"),
    CACHE("кеш Room"),
    FALLBACK("довідкові значення")
}

data class Fetched<T>(val value: T, val origin: DataOrigin)

/**
 * Єдина точка доступу до даних для ViewModel.
 *
 * LOCAL      — тільки Room, мережа не використовується.
 * REMOTE     — тільки REST API, результат у кеш не потрапляє.
 * AGGREGATED — UI завжди читає Room, а репозиторій оновлює кеш із мережі,
 *              якщо він старший за [cacheTtlMs]. Без мережі лишаються останні збережені дані.
 */
class BuildingRepository(
    private val local: LocalBuildingDataSource,
    private val remote: RemoteBuildingDataSource,
    private val errors: ErrorCenter,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = 5 * 60_000L
) {
    fun observeCached(): Flow<List<Building>> = local.observeAll()

    fun observeLastSync(): Flow<Long?> = local.observeLastSync()

    suspend fun fetchRemote(): Outcome<List<Building>> =
        errors.execute("GET /buildings", RetryPolicy.NETWORK) { remote.list() }

    /** Повертає true, якщо кеш оновлено з мережі, і false, якщо він ще свіжий. */
    suspend fun refreshCache(force: Boolean): Outcome<Boolean> {
        val lastSync = local.lastSync()
        if (!force && lastSync != null && clock() - lastSync < cacheTtlMs) return Outcome.Success(false)
        return errors.execute("Оновлення кешу будівель", RetryPolicy.NETWORK) {
            local.replaceServerCopies(remote.list(), clock())
            true
        }
    }

    /** Спершу мережа; якщо вона недоступна, а об'єкт є в кеші, повертається збережена копія. */
    suspend fun details(id: Long): Outcome<Fetched<Building>> {
        if (id < 0) return local.find(id)?.let { Outcome.Success(Fetched(it, DataOrigin.CACHE)) }
            ?: Outcome.Failure(AppError.NotFound)
        val cached = local.find(id)
        val result = errors.execute("GET /buildings/$id", RetryPolicy.NETWORK, notifyUser = cached == null) {
            remote.details(id).also { local.save(it, clock()) }
        }
        return when {
            result is Outcome.Success -> Outcome.Success(Fetched(result.value, DataOrigin.NETWORK))
            cached != null -> {
                errors.notify("Сервер недоступний, показано збережену копію")
                Outcome.Success(Fetched(cached, DataOrigin.CACHE))
            }
            else -> result as Outcome.Failure
        }
    }

    suspend fun save(building: Building, mode: DataSourceMode): Outcome<Building> = when (mode) {
        DataSourceMode.LOCAL -> Outcome.Success(local.save(building, clock()))
        // POST не ідемпотентний, тому повторів немає; PUT повторювати безпечно.
        else -> errors.execute(
            operation = if (building.id > 0) "PUT /buildings/${building.id}" else "POST /buildings",
            policy = if (building.id > 0) RetryPolicy.NETWORK else RetryPolicy.NONE
        ) {
            remote.save(building).also { if (mode == DataSourceMode.AGGREGATED) local.save(it, clock()) }
        }
    }

    suspend fun delete(building: Building, mode: DataSourceMode): Outcome<Unit> = when {
        building.id < 0 -> Outcome.Success(local.delete(building.id))
        mode == DataSourceMode.LOCAL ->
            Outcome.Failure(AppError.Rejected("Серверний запис видаляється лише в режимі «Сервер» або «Агреговано»"))
        else -> errors.execute("DELETE /buildings/${building.id}", RetryPolicy.NETWORK) {
            remote.delete(building.id)
            if (mode == DataSourceMode.AGGREGATED) local.delete(building.id)
            Unit
        }
    }

    /** Тарифи потрібні для розрахунку вартості, тому метод не повертає помилку: у найгіршому разі — довідкові значення. */
    suspend fun tariffs(): Fetched<Tariffs> {
        val cached = local.tariffs()
        if (cached != null && clock() - cached.second < cacheTtlMs) return Fetched(cached.first, DataOrigin.CACHE)
        val result = errors.execute("GET /tariffs", RetryPolicy.NETWORK, notifyUser = false) {
            remote.tariffs().also { local.saveTariffs(it, clock()) }
        }
        return when {
            result is Outcome.Success -> Fetched(result.value, DataOrigin.NETWORK)
            cached != null -> Fetched(cached.first, DataOrigin.CACHE)
            else -> Fetched(Tariffs.FALLBACK, DataOrigin.FALLBACK)
        }
    }
}
