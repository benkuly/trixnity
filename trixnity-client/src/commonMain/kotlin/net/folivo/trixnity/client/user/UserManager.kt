package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId

class UserManager(
    private val store: Store
) {
    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> {
        return store.roomUser.getAll(roomId, scope)
    }

    suspend fun getById(userId: MatrixId.UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> {
        return store.roomUser.get(userId, roomId, scope)
    }
}