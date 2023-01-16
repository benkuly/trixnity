package net.folivo.trixnity.client.room

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.waitForInboundMegolmSession
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import net.folivo.trixnity.olm.OlmLibraryException

private val log = KotlinLogging.logger {}

class MegolmRoomEventDecryptionService(
    private val olmCryptoStore: OlmCryptoStore,
    private val keyBackupService: KeyBackupService,
    private val olmEncryptionService: OlmEncryptionService,
) : RoomEventDecryptionService {
    override suspend fun decrypt(event: Event.RoomEvent<*>): Result<RoomEventContent>? = coroutineScope {
        val content = event.content
        val roomId = event.roomId
        val eventId = event.id
        if (content is EncryptedEventContent.MegolmEncryptedEventContent) {
            val session = olmCryptoStore.getInboundMegolmSession(content.sessionId, roomId).first()
            val firstKnownIndex = session?.firstKnownIndex
            if (session == null) {
                val loadMegolmSessionJob = launch {
                    keyBackupService.loadMegolmSession(roomId, content.sessionId)
                }
                log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                olmCryptoStore.waitForInboundMegolmSession(roomId, content.sessionId)
                loadMegolmSessionJob.cancel()
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
                    log.debug { "unknwon message index, so we request key backup and start to wait for inbound megolm session to decrypt $eventId in $roomId again" }
                    val loadMegolmSessionJob = launch {
                        keyBackupService.loadMegolmSession(roomId, content.sessionId)
                    }
                    olmCryptoStore.waitForInboundMegolmSession(
                        roomId,
                        content.sessionId,
                        firstKnownIndexLessThen = firstKnownIndex
                    )
                    loadMegolmSessionJob.cancel()
                    encryptedEvent.decryptCatching()
                } else decryptEventAttempt
            log.trace { "decrypted TimelineEvent $eventId in $roomId" }
            decryptedEvent
        } else null
    }

    private suspend fun Event.RoomEvent<EncryptedEventContent.MegolmEncryptedEventContent>.decryptCatching(): Result<RoomEventContent> =
        kotlin.runCatching { olmEncryptionService.decryptMegolm(this).content }
}

