package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

@Entity(
    tableName = "RoomAccountData",
    primaryKeys = ["roomId", "type", "key"],
)
internal data class RoomRoomAccountData(
    val roomId: RoomId,
    val type: String,
    val key: String,
    val event: String,
)

@Dao
internal interface RoomAccountDataDao {
    @Query("SELECT * FROM RoomAccountData WHERE roomId = :roomId AND type = :type")
    suspend fun getByTwoKeys(roomId: RoomId, type: String): List<RoomRoomAccountData>

    @Query("SELECT * FROM RoomAccountData WHERE roomId = :roomId AND type = :type AND key = :key LIMIT 1")
    suspend fun getByAllKeys(roomId: RoomId, type: String, key: String): RoomRoomAccountData?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomAccountData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entity: List<RoomRoomAccountData>)

    @Query("DELETE FROM RoomAccountData WHERE roomId = :roomId")
    suspend fun delete(roomId: RoomId)

    @Query("DELETE FROM RoomAccountData WHERE roomId = :roomId AND type = :type")
    suspend fun delete(roomId: RoomId, type: String)

    @Query("DELETE FROM RoomAccountData WHERE roomId = :roomId AND type = :type AND key = :key")
    suspend fun delete(roomId: RoomId, type: String, key: String)

    @Query("DELETE FROM RoomAccountData")
    suspend fun deleteAll()
}

internal class RoomRoomAccountDataRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomAccountDataRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    private val dao = db.roomAccountData()

    override suspend fun get(firstKey: RoomAccountDataRepositoryKey): Map<String, Event.RoomAccountDataEvent<*>> =
        dao.getByTwoKeys(firstKey.roomId, firstKey.type)
            .associate { entity -> entity.key to json.decodeFromString(serializer, entity.event) }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        dao.delete(roomId)
    }

    override suspend fun get(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
    ): Event.RoomAccountDataEvent<*>? =
        dao.getByAllKeys(firstKey.roomId, firstKey.type, secondKey)
            ?.let { entity -> json.decodeFromString(serializer, entity.event) }

    override suspend fun save(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: Event.RoomAccountDataEvent<*>,
    ) {
        dao.insert(
            RoomRoomAccountData(
                roomId = firstKey.roomId,
                type = firstKey.type,
                key = secondKey,
                event = json.encodeToString(serializer, value),
            )
        )
    }

    override suspend fun delete(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ) {
        dao.delete(firstKey.roomId, firstKey.type, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
