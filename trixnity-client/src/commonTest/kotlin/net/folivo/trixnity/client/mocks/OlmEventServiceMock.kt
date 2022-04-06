package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.folivo.trixnity.client.crypto.IOlmEventService
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent

class OlmEventServiceMock(
    override val decryptedOlmEvents: SharedFlow<IOlmService.DecryptedOlmEventContainer> = MutableSharedFlow()
) : IOlmEventService {

    lateinit var returnEncryptOlm:EncryptedEventContent.OlmEncryptedEventContent
    override suspend fun encryptOlm(
        content: EventContent,
        receiverId: UserId,
        deviceId: String,
        forceNewSession: Boolean
    ): EncryptedEventContent.OlmEncryptedEventContent {
        return returnEncryptOlm
    }

    override suspend fun decryptOlm(
        encryptedContent: EncryptedEventContent.OlmEncryptedEventContent,
        senderId: UserId
    ): DecryptedOlmEvent<*> {
        throw NotImplementedError()
    }

    override suspend fun encryptMegolm(
        content: MessageEventContent,
        roomId: RoomId,
        settings: EncryptionEventContent
    ): EncryptedEventContent.MegolmEncryptedEventContent {
        throw NotImplementedError()
    }

    override suspend fun decryptMegolm(encryptedEvent: Event.MessageEvent<EncryptedEventContent.MegolmEncryptedEventContent>): DecryptedMegolmEvent<*> {
        throw NotImplementedError()
    }
}