package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.events.Event
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomState : Table("room_state") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val stateKey = varchar("state_key", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, stateKey)
    val event = text("event")
}

internal class ExposedRoomStateRepository(private val json: Json) : RoomStateRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun getBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? =
        withExposedRead {
            ExposedRoomState.select {
                ExposedRoomState.roomId.eq(firstKey.roomId.full) and
                        ExposedRoomState.type.eq(firstKey.type) and
                        ExposedRoomState.stateKey.eq(secondKey)
            }.firstOrNull()?.let {
                json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
        }

    override suspend fun saveBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>): Unit =
        withExposedWrite {
            ExposedRoomState.replace {
                it[roomId] = firstKey.roomId.full
                it[type] = firstKey.type
                it[stateKey] = secondKey
                it[event] = json.encodeToString(serializer, value)
            }
        }

    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> = withExposedRead {
        ExposedRoomState.select { ExposedRoomState.roomId.eq(key.roomId.full) and ExposedRoomState.type.eq(key.type) }
            .associate {
                it[ExposedRoomState.stateKey] to json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
    }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>): Unit = withExposedWrite {
        ExposedRoomState.batchReplace(value.entries) { (stateKey, event) ->
            this[ExposedRoomState.roomId] = key.roomId.full
            this[ExposedRoomState.type] = key.type
            this[ExposedRoomState.stateKey] = stateKey
            this[ExposedRoomState.event] = json.encodeToString(serializer, event)
        }
    }

    override suspend fun delete(key: RoomStateRepositoryKey): Unit = withExposedWrite {
        ExposedRoomState.deleteWhere { roomId.eq(key.roomId.full) and type.eq(key.type) }
    }

    override suspend fun deleteBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Unit =
        withExposedWrite {
            ExposedRoomState.deleteWhere {
                roomId.eq(firstKey.roomId.full) and
                        type.eq(firstKey.type) and
                        stateKey.eq(secondKey)
            }
        }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomState.deleteAll()
    }
}