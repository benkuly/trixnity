package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.delay
import net.folivo.trixnity.client.room.RoomEventDecryptionService
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEventContent
import kotlin.time.Duration

class RoomEventDecryptionServiceMock(private val dontReturn: Boolean = false) : RoomEventDecryptionService {
    var returnDecrypt: suspend () -> Result<RoomEventContent>? = { null }
    var decryptCounter = 0
    override suspend fun decrypt(event: Event.RoomEvent<*>): Result<RoomEventContent>? {
        decryptCounter++
        if (dontReturn) delay(Duration.INFINITE)
        return returnDecrypt()
    }
}