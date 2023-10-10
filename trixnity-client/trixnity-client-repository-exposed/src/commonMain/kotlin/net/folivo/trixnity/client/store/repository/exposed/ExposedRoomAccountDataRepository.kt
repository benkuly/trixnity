package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepository
import net.folivo.trixnity.client.store.repository.RoomAccountDataRepositoryKey
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomAccountData : Table("room_account_data") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val key = varchar("key", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, key)
    val event = text("event")
}

internal class ExposedRoomAccountDataRepository(private val json: Json) : RoomAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: RoomAccountDataRepositoryKey): Map<String, RoomAccountDataEvent<*>> =
        withExposedRead {
            ExposedRoomAccountData.select {
                ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and
                        ExposedRoomAccountData.type.eq(firstKey.type)
            }.associate {
                it[ExposedRoomAccountData.key] to json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
            }
        }

    override suspend fun deleteByRoomId(roomId: RoomId): Unit = withExposedWrite {
        ExposedRoomAccountData.deleteWhere { ExposedRoomAccountData.roomId.eq(roomId.full) }
    }

    override suspend fun get(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String
    ): RoomAccountDataEvent<*>? = withExposedRead {
        ExposedRoomAccountData.select {
            ExposedRoomAccountData.roomId.eq(firstKey.roomId.full) and
                    ExposedRoomAccountData.type.eq(firstKey.type) and
                    ExposedRoomAccountData.key.eq(secondKey)
        }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedRoomAccountData.event])
        }
    }

    override suspend fun save(
        firstKey: RoomAccountDataRepositoryKey,
        secondKey: String,
        value: RoomAccountDataEvent<*>
    ): Unit = withExposedWrite {
        ExposedRoomAccountData.replace {
            it[roomId] = firstKey.roomId.full
            it[type] = firstKey.type
            it[key] = secondKey
            it[event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun delete(firstKey: RoomAccountDataRepositoryKey, secondKey: String) {
        ExposedRoomAccountData.deleteWhere {
            roomId.eq(firstKey.roomId.full) and
                    type.eq(firstKey.type) and
                    key.eq(secondKey)
        }
    }

    override suspend fun deleteAll() {
        ExposedRoomAccountData.deleteAll()
    }
}