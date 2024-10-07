package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.repository.RoomUserReceiptsRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomUserReceipts : Table("room_user_receipts") {
    val userId = varchar("user_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(userId, roomId)
    val value = text("value")
}

internal class ExposedRoomUserReceiptsRepository(private val json: Json) : RoomUserReceiptsRepository {
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUserReceipts? = withExposedRead {
        ExposedRoomUserReceipts.selectAll().where {
            ExposedRoomUserReceipts.roomId.eq(firstKey.full) and ExposedRoomUserReceipts.userId.eq(
                secondKey.full
            )
        }
            .firstOrNull()?.let {
                json.decodeFromString(it[ExposedRoomUserReceipts.value])
            }
    }

    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUserReceipts): Unit =
        withExposedWrite {
            ExposedRoomUserReceipts.upsert {
                it[roomId] = firstKey.full
                it[userId] = secondKey.full
                it[ExposedRoomUserReceipts.value] = json.encodeToString(value)
            }
        }

    override suspend fun delete(firstKey: RoomId, secondKey: UserId): Unit = withExposedWrite {
        ExposedRoomUserReceipts.deleteWhere { roomId.eq(firstKey.full) and userId.eq(secondKey.full) }
    }

    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomUserReceipts.deleteWhere { this.roomId.eq(roomId.full) }
    }

    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUserReceipts> = withExposedRead {
        ExposedRoomUserReceipts.select { ExposedRoomUserReceipts.roomId eq firstKey.full }
            .map { json.decodeFromString<RoomUserReceipts>(it[ExposedRoomUserReceipts.value]) }
            .associateBy { it.userId }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedRoomUserReceipts.deleteAll()
    }
}