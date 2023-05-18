package net.folivo.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event

@Entity(
    tableName = "RoomState",
    primaryKeys = ["roomId", "type", "stateKey"],
)
internal data class RoomRoomState(
    val roomId: RoomId,
    val type: String,
    val stateKey: String,
    val event: String,
)

@Dao
internal interface RoomStateDao {
    @Query("SELECT * FROM RoomState WHERE roomId = :roomId AND type = :type")
    suspend fun get(roomId: RoomId, type: String): List<RoomRoomState>

    @Query("SELECT * FROM RoomState WHERE roomId = :roomId AND type = :type AND stateKey = :stateKey LIMIT 1")
    suspend fun get(roomId: RoomId, type: String, stateKey: String): RoomRoomState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomRoomState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RoomRoomState>)

    @Query("DELETE FROM RoomState WHERE roomId = :roomId AND type = :type")
    suspend fun delete(roomId: RoomId, type: String)

    @Query("DELETE FROM RoomState WHERE roomId = :roomId AND type = :type AND stateKey = :stateKey")
    suspend fun delete(roomId: RoomId, type: String, stateKey: String)

    @Query("DELETE FROM RoomState")
    suspend fun deleteAll()
}

internal class RoomRoomStateRepository(
    db: TrixnityRoomDatabase,
    private val json: Json,
) : RoomStateRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: error("could not find event serializer")

    private val dao = db.roomState()

    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> =
        dao.get(key.roomId, key.type)
            .associate { entity ->
                entity.stateKey to json.decodeFromString(
                    serializer,
                    entity.event
                )
            }

    override suspend fun getBySecondKey(
        firstKey: RoomStateRepositoryKey,
        secondKey: String
    ): Event<*>? =
        dao.get(firstKey.roomId, firstKey.type, stateKey = secondKey)
            ?.let { entity -> json.decodeFromString(serializer, entity.event) }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>) {
        dao.insertAll(
            value.map { (stateKey, event) ->
                RoomRoomState(
                    roomId = key.roomId,
                    type = key.type,
                    stateKey = stateKey,
                    event = json.encodeToString(serializer, event),
                )
            }
        )
    }

    override suspend fun saveBySecondKey(
        firstKey: RoomStateRepositoryKey,
        secondKey: String,
        value: Event<*>
    ) {
        dao.insert(
            RoomRoomState(
                roomId = firstKey.roomId,
                type = firstKey.type,
                stateKey = secondKey,
                event = json.encodeToString(serializer, value),
            )
        )
    }

    override suspend fun delete(key: RoomStateRepositoryKey) {
        dao.delete(key.roomId, key.type)
    }

    override suspend fun deleteBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String) {
        dao.delete(firstKey.roomId, firstKey.type, secondKey)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
