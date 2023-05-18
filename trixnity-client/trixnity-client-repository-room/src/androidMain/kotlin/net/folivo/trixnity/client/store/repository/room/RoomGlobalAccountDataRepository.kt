package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event

@Entity(
    tableName = "GlobalAccountData",
    primaryKeys = ["type", "key"],
)
internal data class RoomGlobalAccountData(
    val type: String,
    val key: String,
    val event: String,
)

@Dao
internal interface GlobalAccountDataDao {
    @Query("SELECT * FROM GlobalAccountData WHERE type = :type")
    suspend fun getAllByType(type: String): List<RoomGlobalAccountData>

    @Query("SELECT * FROM GlobalAccountData WHERE type = :type AND key = :key LIMIT 1")
    suspend fun getByKeys(type: String, key: String): RoomGlobalAccountData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomGlobalAccountData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoomGlobalAccountData>)

    @Query("DELETE FROM GlobalAccountData WHERE type = :type")
    suspend fun delete(type: String)

    @Query("DELETE FROM GlobalAccountData WHERE type = :type AND key = :key")
    suspend fun delete(type: String, key: String)

    @Query("DELETE FROM GlobalAccountData")
    suspend fun deleteAll()
}

internal class RoomGlobalAccountDataRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : GlobalAccountDataRepository {
    private val dao = db.globalAccountData()

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule
        .getContextual(Event.GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: String): Map<String, Event.GlobalAccountDataEvent<*>> =
        dao.getAllByType(key)
            .associate { entity -> entity.key to json.decodeFromString(serializer, entity.event) }

    override suspend fun getBySecondKey(
        firstKey: String,
        secondKey: String
    ): Event.GlobalAccountDataEvent<*>? =
        dao.getByKeys(firstKey, secondKey)
            ?.let { entity -> json.decodeFromString(serializer, entity.event) }

    override suspend fun save(key: String, value: Map<String, Event.GlobalAccountDataEvent<*>>) {
        dao.insertAll(
            value.map { (entityKey, eventJson) ->
                RoomGlobalAccountData(
                    type = key,
                    key = entityKey,
                    event = json.encodeToString(serializer, eventJson),
                )
            }
        )
    }

    override suspend fun saveBySecondKey(
        firstKey: String,
        secondKey: String,
        value: Event.GlobalAccountDataEvent<*>
    ) {
        dao.insert(
            RoomGlobalAccountData(
                type = firstKey,
                key = secondKey,
                event = json.encodeToString(serializer, value),
            )
        )
    }

    override suspend fun delete(key: String) {
        dao.delete(key)
    }

    override suspend fun deleteBySecondKey(firstKey: String, secondKey: String) {
        dao.delete(firstKey, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
