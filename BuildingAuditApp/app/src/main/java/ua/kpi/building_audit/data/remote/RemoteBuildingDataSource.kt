package ua.kpi.building_audit.data.remote

import retrofit2.HttpException
import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Tariffs

/** Віддалене джерело: лише Retrofit-виклики і перетворення DTO на доменні об'єкти. */
class RemoteBuildingDataSource(private val api: EnergyAuditApi) {
    suspend fun list(): List<Building> = api.buildings().map { it.toDomain() }

    suspend fun details(id: Long): Building = api.building(id).toDomain()

    suspend fun save(building: Building): Building =
        if (building.id > 0) api.update(building.id, building.toDto()).toDomain()
        else api.create(building.toDto().copy(id = null)).toDomain()

    suspend fun delete(id: Long) {
        val response = api.delete(id)
        if (!response.isSuccessful) throw HttpException(response)
    }

    suspend fun tariffs(): Tariffs = api.tariffs().toDomain()
}
