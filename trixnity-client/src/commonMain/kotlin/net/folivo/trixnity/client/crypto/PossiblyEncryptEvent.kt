package net.folivo.trixnity.client.crypto

import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.crypto.olm.IOlmEncryptionService

interface IPossiblyEncryptEvent {
    suspend operator fun invoke(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent>
}

class PossiblyEncryptEvent(
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val olmEncryptionService: IOlmEncryptionService,
    private val userService: IUserService
) : IPossiblyEncryptEvent {
    override suspend operator fun invoke(
        content: MessageEventContent,
        roomId: RoomId,
    ): Result<MessageEventContent> {
        return if (roomStore.get(roomId).first()?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
            userService.loadMembers(roomId)

            val megolmSettings = roomStateStore.getByStateKey<EncryptionEventContent>(roomId).first()?.content
            requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
            kotlin.runCatching { olmEncryptionService.encryptMegolm(content, roomId, megolmSettings) }
        } else Result.success(content)
    }
}