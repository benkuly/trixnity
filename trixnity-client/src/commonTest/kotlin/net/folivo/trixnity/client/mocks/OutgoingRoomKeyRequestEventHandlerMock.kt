package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import net.folivo.trixnity.core.model.RoomId

class OutgoingRoomKeyRequestEventHandlerMock : OutgoingRoomKeyRequestEventHandler {
    val requestRoomKeysCalled: MutableStateFlow<List<Pair<RoomId, String>>> = MutableStateFlow(listOf())
    override suspend fun requestRoomKeys(roomId: RoomId, sessionId: String) {
        requestRoomKeysCalled.update { it + (roomId to sessionId) }
    }
}