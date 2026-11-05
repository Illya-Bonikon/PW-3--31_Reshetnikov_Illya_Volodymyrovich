package ua.kpi.building_audit.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Будівля зберігається як JSON того самого DTO, що приходить із сервера:
 * вкладену оболонку з шарами не доводиться розкладати на окремі таблиці.
 */
@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val json: String,
    val savedAt: Long
)

/** Довільні кешовані відповіді за ключем (тарифи, час останньої синхронізації). */
@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long
)

@Dao
abstract class AuditDao {
    @Query("SELECT * FROM buildings ORDER BY name")
    abstract fun observeBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings WHERE id = :id")
    abstract suspend fun building(id: Long): BuildingEntity?

    @Query("SELECT MIN(id) FROM buildings")
    abstract suspend fun minId(): Long?

    @Upsert
    abstract suspend fun upsert(entity: BuildingEntity)

    @Upsert
    abstract suspend fun upsertAll(entities: List<BuildingEntity>)

    @Query("DELETE FROM buildings WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM buildings WHERE id > 0")
    abstract suspend fun deleteServerCopies()

    /** Серверні записи замінюються повністю, записи з від'ємним id (створені локально) лишаються. */
    @Transaction
    open suspend fun replaceServerCopies(entities: List<BuildingEntity>) {
        deleteServerCopies()
        upsertAll(entities)
    }

    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    abstract suspend fun entry(key: String): CacheEntry?

    @Query("SELECT * FROM cache_entries WHERE `key` = :key")
    abstract fun observeEntry(key: String): Flow<CacheEntry?>

    @Upsert
    abstract suspend fun upsertEntry(entry: CacheEntry)
}

@Database(entities = [BuildingEntity::class, CacheEntry::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun dao(): AuditDao

    companion object {
        fun create(context: Context): AuditDatabase =
            Room.databaseBuilder(context, AuditDatabase::class.java, "energy_audit_pr3.db").build()
    }
}
