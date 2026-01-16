package de.connect2x.trixnity.client.store.repository.room

import androidx.room.*
import de.connect2x.trixnity.client.store.repository.MigrationRepository

@Entity(tableName = "Migration")
data class RoomMigration(
    @PrimaryKey val name: String,
    val metadata: String? = null,
)

@Dao
interface MigrationDao {
    @Query("SELECT * FROM Migration WHERE name = :name LIMIT 1")
    suspend fun get(name: String): RoomMigration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomMigration)

    @Query("DELETE FROM Migration WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM Migration")
    suspend fun deleteAll()
}

internal class RoomMigrationRepository(
    db: TrixnityRoomDatabase,
) : MigrationRepository {

    private val dao = db.migration()

    override suspend fun get(key: String): String? = withRoomRead { dao.get(key)?.metadata }
    override suspend fun save(key: String, value: String) = withRoomWrite { dao.insert(RoomMigration(key, value)) }
    override suspend fun delete(key: String) = withRoomWrite { dao.delete(key) }
    override suspend fun deleteAll() = withRoomWrite { dao.deleteAll() }
}
