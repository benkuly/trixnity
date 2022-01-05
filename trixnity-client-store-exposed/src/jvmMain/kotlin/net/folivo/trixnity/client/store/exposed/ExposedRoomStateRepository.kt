package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomStateRepository
import net.folivo.trixnity.client.store.repository.RoomStateRepositoryKey
import net.folivo.trixnity.core.model.events.Event
import org.jetbrains.exposed.sql.*

internal object ExposedRoomState : Table("room_state") {
    val roomId = varchar("room_id", length = 65535)
    val type = varchar("type", length = 65535)
    val stateKey = varchar("state_key", length = 65535)
    override val primaryKey = PrimaryKey(roomId, type, stateKey)
    val event = text("event")
}

internal class ExposedRoomStateRepository(private val json: Json) : RoomStateRepository {

    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun getBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String): Event<*>? {
        return ExposedRoomState.select {
            ExposedRoomState.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomState.type.eq(firstKey.type) and
                    ExposedRoomState.stateKey.eq(secondKey)
        }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedRoomState.event])
        }
    }

    override suspend fun saveBySecondKey(firstKey: RoomStateRepositoryKey, secondKey: String, value: Event<*>) {
        ExposedRoomState.replace {
            it[this.roomId] = firstKey.roomId.full
            it[this.type] = firstKey.type
            it[this.stateKey] = secondKey
            it[this.event] = json.encodeToString(serializer, value)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun get(key: RoomStateRepositoryKey): Map<String, Event<*>> {
        return ExposedRoomState.select { ExposedRoomState.roomId.eq(key.roomId.full) and ExposedRoomState.type.eq(key.type) }
            .associate {
                it[ExposedRoomState.stateKey] to json.decodeFromString(serializer, it[ExposedRoomState.event])
            }
    }

    override suspend fun save(key: RoomStateRepositoryKey, value: Map<String, Event<*>>) {
        ExposedRoomState.batchReplace(value.entries) { (stateKey, event) ->
            this[ExposedRoomState.roomId] = key.roomId.full
            this[ExposedRoomState.type] = key.type
            this[ExposedRoomState.stateKey] = stateKey
            this[ExposedRoomState.event] = json.encodeToString(serializer, event)
        }
    }

    override suspend fun delete(key: RoomStateRepositoryKey) {
        ExposedRoomState.deleteWhere { ExposedRoomState.roomId.eq(key.roomId.full) and ExposedRoomState.type.eq(key.type) }
    }
}