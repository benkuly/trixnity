package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.client.room.RoomEventDecryptionService
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEventContent

class RoomEventDecryptionServiceMock : RoomEventDecryptionService {
    var returnDecrypt: suspend () -> Result<RoomEventContent>? = { null }
    override suspend fun decrypt(event: Event.RoomEvent<*>): Result<RoomEventContent>? {
        return returnDecrypt()
    }
}