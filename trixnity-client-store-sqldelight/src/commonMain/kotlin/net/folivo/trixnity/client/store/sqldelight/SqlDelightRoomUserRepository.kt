package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightRoomUserRepository(
    private val db: RoomUserQueries,
    private val json: Json,
    private val context: CoroutineContext
) : RoomUserRepository {
    override suspend fun getByUserId(userId: UserId, roomId: RoomId): RoomUser? = withContext(context) {
        db.getRoomUser(userId.full, roomId.full).executeAsOneOrNull()?.let {
            json.decodeFromString(it)
        }
    }

    override suspend fun saveByUserId(userId: UserId, roomId: RoomId, roomUser: RoomUser) = withContext(context) {
        db.saveRoomUser(userId.full, roomId.full, json.encodeToString(roomUser))
    }

    override suspend fun deleteByUserId(userId: UserId, roomId: RoomId) = withContext(context) {
        db.deleteRoomUser(userId.full, roomId.full)
    }

    override suspend fun get(key: RoomId): Map<UserId, RoomUser> = withContext(context) {
        db.getRoomUsers(key.full).executeAsList().map {
            json.decodeFromString<RoomUser>(it.room_user)
        }.associateBy { it.userId }
    }

    override suspend fun save(key: RoomId, value: Map<UserId, RoomUser>) = withContext(context) {
        value.values.forEach { saveByUserId(it.userId, key, it) }
    }

    override suspend fun delete(key: RoomId) = withContext(context) {
        db.deleteRoomUsers(key.full)
    }
}