package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedMessageEventContent
import de.connect2x.trixnity.core.model.events.mergeContentOrNull
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val log = Logger("de.connect2x.trixnity.client.room.StickyRoomEventHandler")

@MSC4354
class StickyEventHandler(
    private val api: MatrixClientServerApiClient,
    private val stickyEventStore: StickyEventStore,
    private val roomEventEncryptionServices: List<RoomEventEncryptionService>,
    private val clock: Clock,
    private val tm: TransactionManager,
    private val config: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (config.experimentalFeatures.enableMSC4354.not()) return
        api.sync.subscribeEventList(Priority.STORE_EVENTS, subscriber = ::setStickyEvents)
            .unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList(Priority.STORE_EVENTS, subscriber = ::setEncryptedStickyEvents)
            .unsubscribeOnCompletion(scope)
        scope.launch { removeInvalidStickyEvents() }
    }

    internal suspend fun removeInvalidStickyEvents() {
        while (currentCoroutineContext().isActive) {
            delay(2.minutes)
            stickyEventStore.deleteInvalid()
        }
    }

    internal suspend fun setStickyEvents(stickyEvents: List<ClientEvent.RoomEvent<StickyEventContent>>) {
        if (stickyEvents.isNotEmpty()) {
            val now = clock.now()
            tm.writeTransaction {
                for (stickyEvent in stickyEvents) {
                    val sticky = stickyEvent.sticky ?: continue
                    val startTime = minOf(now, Instant.fromEpochMilliseconds(stickyEvent.originTimestamp))
                    val stickyDuration = sticky.durationMs.milliseconds.coerceIn(ZERO..1.hours)
                    val endTime = startTime + stickyDuration
                    stickyEventStore.save(
                        StoredStickyEvent(
                            event = stickyEvent,
                            startTime = startTime,
                            endTime = endTime
                        )
                    )
                }
            }
        }
    }

    internal suspend fun setEncryptedStickyEvents(encryptedEvents: List<ClientEvent.RoomEvent.MessageEvent<EncryptedMessageEventContent>>) {
        val encryptedStickyEvents = encryptedEvents.filter { it.sticky != null }
        if (encryptedStickyEvents.isNotEmpty()) {
            val stickyEvents = coroutineScope {
                encryptedStickyEvents.map { encryptedStickyEvent ->
                    async {
                        val decryptedStickyEventContent = withTimeoutOrNull(3.seconds) {
                            roomEventEncryptionServices.decrypt(encryptedStickyEvent)
                                ?.onFailure { log.warn { "could not decrypt sticky event ${encryptedStickyEvent.id}" } }
                                ?.getOrNull() as? StickyEventContent
                        } ?: return@async null
                        encryptedStickyEvent.mergeContentOrNull(decryptedStickyEventContent)
                    }
                }.awaitAll().filterNotNull()
            }
            setStickyEvents(stickyEvents)
        }
    }
}
