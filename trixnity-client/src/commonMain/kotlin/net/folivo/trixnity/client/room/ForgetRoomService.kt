package net.folivo.trixnity.client.room

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.Membership

fun interface ForgetRoomService {
    suspend operator fun invoke(roomId: RoomId)
}

class ForgetRoomServiceImpl(
    private val roomStore: RoomStore,
    private val roomUserStore: RoomUserStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomTimelineStore: RoomTimelineStore,
) : ForgetRoomService {
    override suspend fun invoke(roomId: RoomId) {
        if (roomStore.get(roomId).first()?.membership == Membership.LEAVE) {
            roomStore.delete(roomId)
            roomTimelineStore.deleteByRoomId(roomId)
            roomStateStore.deleteByRoomId(roomId)
            roomAccountDataStore.deleteByRoomId(roomId)
            roomUserStore.deleteByRoomId(roomId)
        }
    }

}