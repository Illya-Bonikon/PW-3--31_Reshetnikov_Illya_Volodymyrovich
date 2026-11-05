package ua.kpi.building_audit.data.local

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ua.kpi.building_audit.data.remote.BuildingDto
import ua.kpi.building_audit.data.remote.toDomain
import ua.kpi.building_audit.data.remote.toDto
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs

/** Локальне джерело на Room. Перетворення в JSON і назад використовує ті самі мапери, що й мережа. */
class LocalBuildingDataSource(private val dao: AuditDao, private val gson: Gson) {

    fun observeAll(): Flow<List<Building>> =
        dao.observeBuildings().map { rows -> rows.map { gson.fromJson(it.json, BuildingDto::class.java).toDomain() } }

    fun observeLastSync(): Flow<Long?> = dao.observeEntry(KEY_LAST_SYNC).map { it?.savedAt }

    suspend fun lastSync(): Long? = dao.entry(KEY_LAST_SYNC)?.savedAt

    suspend fun find(id: Long): Building? =
        dao.building(id)?.let { gson.fromJson(it.json, BuildingDto::class.java).toDomain() }

    suspend fun replaceServerCopies(items: List<Building>, now: Long) {
        dao.replaceServerCopies(items.map { it.toEntity(now) })
        dao.upsertEntry(CacheEntry(KEY_LAST_SYNC, "", now))
    }

    suspend fun save(building: Building, now: Long): Building {
        val stored = if (building.id == Building.NEW_ID) building.copy(id = minOf(dao.minId() ?: 0L, 0L) - 1) else building
        dao.upsert(stored.toEntity(now))
        return stored
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun saveTariffs(tariffs: Tariffs, now: Long) =
        dao.upsertEntry(CacheEntry(KEY_TARIFFS, gson.toJson(tariffs), now))

    suspend fun tariffs(): Pair<Tariffs, Long>? =
        dao.entry(KEY_TARIFFS)?.let { gson.fromJson(it.json, Tariffs::class.java) to it.savedAt }

    private fun Building.toEntity(now: Long) = BuildingEntity(id, name, gson.toJson(toDto()), now)

    private companion object {
        const val KEY_LAST_SYNC = "buildings_last_sync"
        const val KEY_TARIFFS = "tariffs"
    }
}
