package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedRoomUser : Table("room_user") {
    val userId = varchar("user_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(userId, roomId)
    val value = text("value")
}

internal class ExposedRoomUserRepository(private val json: Json) : RoomUserRepository {
    override suspend fun getBySecondKey(firstKey: RoomId, secondKey: UserId): RoomUser? {
        return ExposedRoomUser.select { ExposedRoomUser.roomId.eq(firstKey.full) and ExposedRoomUser.userId.eq(secondKey.full) }
            .firstOrNull()?.let {
                json.decodeFromString(it[ExposedRoomUser.value])
            }
    }

    override suspend fun saveBySecondKey(firstKey: RoomId, secondKey: UserId, value: RoomUser) {
        ExposedRoomUser.replace {
            it[roomId] = firstKey.full
            it[userId] = secondKey.full
            it[ExposedRoomUser.value] = json.encodeToString(value)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: RoomId, secondKey: UserId) {
        ExposedRoomUser.deleteWhere { roomId.eq(firstKey.full) and userId.eq(secondKey.full) }
    }

    override suspend fun get(key: RoomId): Map<UserId, RoomUser> {
        return ExposedRoomUser.select { ExposedRoomUser.roomId eq key.full }
            .map { json.decodeFromString<RoomUser>(it[ExposedRoomUser.value]) }
            .associateBy { it.userId }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) {
        ExposedRoomUser.batchReplace(value.values) { replaceValue ->
            this[ExposedRoomUser.userId] = replaceValue.userId.full
            this[ExposedRoomUser.roomId] = key.full
            this[ExposedRoomUser.value] = json.encodeToString(replaceValue)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedRoomUser.deleteWhere { roomId eq key.full }
    }

    override suspend fun deleteAll() {
        ExposedRoomUser.deleteAll()
    }
}