package net.folivo.trixnity.client.room

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import net.folivo.trixnity.client.key.IKeyBackupService
import net.folivo.trixnity.client.store.OlmStore
import net.folivo.trixnity.client.store.waitForInboundMegolmSession
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.crypto.olm.IOlmEncryptionService
import net.folivo.trixnity.olm.OlmLibraryException

private val log = KotlinLogging.logger {}

class MegolmRoomEventDecryptionService(
    private val olmStore: OlmStore,
    private val keyBackupService: IKeyBackupService,
    private val olmEncryptionService: IOlmEncryptionService,
) : RoomEventDecryptionService {
    override suspend fun decrypt(event: Event.RoomEvent<*>): Result<RoomEventContent>? = coroutineScope {
        val content = event.content
        val roomId = event.roomId
        val eventId = event.id
        if (content is EncryptedEventContent.MegolmEncryptedEventContent) {
            val session = olmStore.getInboundMegolmSession(content.sessionId, roomId, this)
            val firstKnownIndex = session.value?.firstKnownIndex
            if (session.value == null) {
                keyBackupService.loadMegolmSession(roomId, content.sessionId)
                log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                olmStore.waitForInboundMegolmSession(roomId, content.sessionId, this)
            }
            log.trace { "try to decrypt event $eventId in $roomId" }
            @Suppress("UNCHECKED_CAST")
            val encryptedEvent = event as Event.RoomEvent<EncryptedEventContent.MegolmEncryptedEventContent>

            val decryptEventAttempt = event.decryptCatching()
            val exception = decryptEventAttempt.exceptionOrNull()
            val decryptedEvent =
                if (exception is OlmLibraryException && exception.message?.contains("UNKNOWN_MESSAGE_INDEX") == true
                    || exception is DecryptionException.SessionException && exception.cause?.message
                        ?.contains("UNKNOWN_MESSAGE_INDEX") == true
                ) {
                    keyBackupService.loadMegolmSession(roomId, content.sessionId)
                    log.debug { "unknwon message index, so we start to wait for inbound megolm session to decrypt $eventId in $roomId again" }
                    olmStore.waitForInboundMegolmSession(
                        roomId,
                        content.sessionId,
                        this,
                        firstKnownIndexLessThen = firstKnownIndex
                    )
                    encryptedEvent.decryptCatching()
                } else decryptEventAttempt
            log.trace { "decrypted TimelineEvent $eventId in $roomId" }
            decryptedEvent
        } else null
    }

    private suspend fun Event.RoomEvent<EncryptedEventContent.MegolmEncryptedEventContent>.decryptCatching(): Result<RoomEventContent> {
        return try {
            Result.success(olmEncryptionService.decryptMegolm(this).content)
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            else Result.failure(ex)
        }
    }
}

