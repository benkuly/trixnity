package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
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
    private val serializer = json.serializersModule.getContextual(StateBaseEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: RoomStateRepositoryKey): Map<String, StateBaseEvent<*>> = withExposedRead {
        ExposedRoomState.select { ExposedRoomState.roomId.eq(firstKey.roomId.full) and ExposedRoomState.type.eq(firstKey.type) }
            .associate {
                it[ExposedRoomState.stateKey] to json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
    }

    override suspend fun get(firstKey: RoomStateRepositoryKey, secondKey: String): StateBaseEvent<*>? =
        withExposedRead {
            ExposedRoomState.select {
                ExposedRoomState.roomId.eq(firstKey.roomId.full) and
                        ExposedRoomState.type.eq(firstKey.type) and
                        ExposedRoomState.stateKey.eq(secondKey)
            }.firstOrNull()?.let {
                json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
        }

    override suspend fun getByRooms(roomIds: Set<RoomId>, type: String, stateKey: String): List<StateBaseEvent<*>> =
        withExposedRead {
            ExposedRoomState.select {
                ExposedRoomState.roomId.inList(roomIds.map { it.full }) and
                        ExposedRoomState.type.eq(type) and
                        ExposedRoomState.stateKey.eq(stateKey)
            }.toList().map {
                json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
        }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withExposedWrite {
        ExposedRoomState.deleteWhere { ExposedRoomState.roomId.eq(roomId.full) }
    }


    override suspend fun save(firstKey: RoomStateRepositoryKey, secondKey: String, value: StateBaseEvent<*>): Unit =
        withExposedWrite {
            ExposedRoomState.replace {
                it[roomId] = firstKey.roomId.full
                it[type] = firstKey.type
                it[stateKey] = secondKey
                it[event] = json.encodeToString(serializer, value)
            }
        }

    override suspend fun delete(firstKey: RoomStateRepositoryKey, secondKey: String): Unit =
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