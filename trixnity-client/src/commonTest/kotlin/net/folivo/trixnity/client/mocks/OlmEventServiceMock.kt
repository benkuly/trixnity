package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.crypto.olm.IOlmEventService
import net.folivo.trixnity.crypto.olm.IOlmMachine

class OlmEventServiceMock(
    override val decryptedOlmEvents: SharedFlow<IOlmMachine.DecryptedOlmEventContainer> = MutableSharedFlow()
) : IOlmEventService {

    lateinit var returnEncryptOlm: () -> EncryptedEventContent.OlmEncryptedEventContent
    var encryptOlmCalled: Triple<EventContent, UserId, String>? = null
    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): EncryptedEventContent.OlmEncryptedEventContent {
        encryptOlmCalled = Triple(content, receiverId, deviceId)
        return returnEncryptOlm()
    }

    lateinit var returnDecryptOlm: DecryptedOlmEvent<*>
    override suspend fun decryptOlm(
        encryptedContent: EncryptedEventContent.OlmEncryptedEventContent,
        senderId: UserId
    ): DecryptedOlmEvent<*> {
        return returnDecryptOlm
    }

    lateinit var returnEncryptMegolm: () -> EncryptedEventContent.MegolmEncryptedEventContent
    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): EncryptedEventContent.MegolmEncryptedEventContent {
        return returnEncryptMegolm()
    }

    val returnDecryptMegolm = mutableListOf<() -> DecryptedMegolmEvent<*>>()
    override suspend fun decryptMegolm(encryptedEvent: Event.MessageEvent<EncryptedEventContent.MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        val returner =
            if (returnDecryptMegolm.size > 1) returnDecryptMegolm.removeFirst()
            else returnDecryptMegolm.first()
        return returner()
    }
}