package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.*

internal object ExposedRoomUser : Table("room_user") {
    val userId = varchar("user_id", length = 65535)
    val roomId = varchar("room_id", length = 65535)
    override val primaryKey = PrimaryKey(userId, roomId)
    val roomUser = text("room_user")
}

internal class ExposedRoomUserRepository(private val json: Json) : RoomUserRepository {
    override suspend fun getByUserId(userId: UserId, roomId: RoomId): RoomUser? {
        return ExposedRoomUser.select { ExposedRoomUser.userId.eq(userId.full) and ExposedRoomUser.roomId.eq(roomId.full) }
            .firstOrNull()?.let {
                json.decodeFromString(it[ExposedRoomUser.roomUser])
            }
    }

    override suspend fun saveByUserId(userId: UserId, roomId: RoomId, roomUser: RoomUser) {
        ExposedRoomUser.replace {
            it[this.userId] = userId.full
            it[this.roomId] = roomId.full
            it[this.roomUser] = json.encodeToString(roomUser)
        }
    }

    override suspend fun deleteByUserId(userId: UserId, roomId: RoomId) {
        ExposedRoomUser.deleteWhere { ExposedRoomUser.userId.eq(userId.full) and ExposedRoomUser.roomId.eq(roomId.full) }
    }

    override suspend fun get(key: RoomId): Map<UserId, RoomUser> {
        return ExposedRoomUser.select { ExposedRoomUser.roomId eq key.full }
            .map { json.decodeFromString<RoomUser>(it[ExposedRoomUser.roomUser]) }
            .associateBy { it.userId }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) {
        ExposedRoomUser.batchReplace(value.values) { replaceValue ->
            this[ExposedRoomUser.userId] = replaceValue.userId.full
            this[ExposedRoomUser.roomId] = key.full
            this[ExposedRoomUser.roomUser] = json.encodeToString(replaceValue)
        }
    }

    override suspend fun delete(key: RoomId) {
        ExposedRoomUser.deleteWhere { ExposedRoomUser.roomId eq key.full }
    }
}