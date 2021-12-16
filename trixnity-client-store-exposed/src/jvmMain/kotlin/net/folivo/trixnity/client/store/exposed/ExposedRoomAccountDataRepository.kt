package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.events.Event
import org.jetbrains.exposed.sql.*

internal object ExposedRoomAccountData : Table("room_account_data") {
    val roomId = varchar("room_id", length = 65535)
    val type = varchar("type", length = 65535)
    override val primaryKey = PrimaryKey(roomId, type)
    val event = text("event")
}

internal class ExposedRoomAccountDataRepository(private val json: Json) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomAccountDataRepositoryKey): Event.RoomAccountDataEvent<*>? {
        return ExposedRoomAccountData.select {
            ExposedRoomAccountData.roomId.eq(key.roomId.full) and
                    ExposedRoomAccountData.type.eq(key.type)
        }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
        }
    }

    override suspend fun save(key: RoomAccountDataRepositoryKey, value: Event.RoomAccountDataEvent<*>) {
        ExposedRoomAccountData.replace {
            it[roomId] = key.roomId.full
            it[type] = key.type
            it[event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun delete(key: RoomAccountDataRepositoryKey) {
        ExposedRoomAccountData.deleteWhere {
            ExposedRoomAccountData.roomId.eq(key.roomId.full) and
                    ExposedRoomAccountData.type.eq(key.type)
        }
    }

}