package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

interface PossiblyEncryptEvent {  // TODO should be aware of encryption algorithms like RoomEventDecryptionService
    suspend operator fun invoke(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>
}

class PossiblyEncryptEventImpl(
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val olmEncryptionService: OlmEncryptionService,
    private val userService: UserService
) : PossiblyEncryptEvent {
    override suspend operator fun invoke(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent> {
        return if (roomStore.get(roomId).first()?.encryptionAlgorithm == EncryptionAlgorithm.Megolm
            && content !is ReactionEventContent
        ) {
            userService.loadMembers(roomId)

            val megolmSettings = roomStateStore.getByStateKey<EncryptionEventContent>(roomId).first()?.content
            runCatching {
                requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
                olmEncryptionService.encryptMegolm(content, roomId, megolmSettings)
            }
        } else Result.success(content)
    }
}