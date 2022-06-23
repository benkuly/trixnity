package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.events.ClientEvent
import org.jetbrains.exposed.sql.*

internal object ExposedRoomAccountData : Table("room_account_data") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val key = varchar("key", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, key)
    val event = text("event")
}

internal class ExposedRoomAccountDataRepository(private val json: Json) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(ClientEvent.RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: RoomAccountDataRepositoryKey): Map<String, ClientEvent.RoomAccountDataEvent<*>> {
        return ExposedRoomAccountData.select {
            ExposedRoomAccountData.roomId.eq(key.roomId.full) and
                    ExposedRoomAccountData.type.eq(key.type)
        }.associate {
            it[ExposedRoomAccountData.key] to json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
        }
    }

    override suspend fun save(key: RoomAccountDataRepositoryKey, value: Map<String, ClientEvent.RoomAccountDataEvent<*>>) {
        ExposedRoomAccountData.batchReplace(value.entries) { (secondKey, event) ->
            this[ExposedRoomAccountData.roomId] = key.roomId.full
            this[ExposedRoomAccountData.type] = key.type
            this[ExposedRoomAccountData.key] = secondKey
            this[ExposedRoomAccountData.event] = json.encodeToString(serializer, event)
        }
    }

    override suspend fun delete(key: RoomAccountDataRepositoryKey) {
        ExposedRoomAccountData.deleteWhere {
            ExposedRoomAccountData.roomId.eq(key.roomId.full) and
                    ExposedRoomAccountData.type.eq(key.type)
        }
    }

    override suspend fun getBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ): ClientEvent.RoomAccountDataEvent<*>? {
        return ExposedRoomAccountData.select {
            ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomAccountData.type.eq(firstKey.type) and
                    ExposedRoomAccountData.key.eq(secondKey)
        }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
        }
    }

    override suspend fun saveBySecondKey(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: ClientEvent.RoomAccountDataEvent<*>
    ) {
        ExposedRoomAccountData.replace {
            it[this.roomId] = firstKey.roomId.full
            it[this.type] = firstKey.type
            it[this.key] = secondKey
            it[this.event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: RoomAccountDataRepositoryKey, secondKey: String) {
        ExposedRoomAccountData.deleteWhere {
            ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomAccountData.type.eq(firstKey.type) and
                    ExposedRoomAccountData.key.eq(secondKey)
        }
    }

    override suspend fun deleteAll() {
        ExposedRoomAccountData.deleteAll()
    }
}