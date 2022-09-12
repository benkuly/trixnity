package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.client.crypto.IPossiblyEncryptEvent
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent

class PossiblyEncryptEventMock : IPossiblyEncryptEvent {
    lateinit var returnEncryptMegolm: suspend (MessageEventContent) -> MessageEventContent
    override suspend fun invoke(content: MessageEventContent, roomId: RoomId): Result<MessageEventContent> {
        return kotlin.runCatching { returnEncryptMegolm(content) }
    }
}