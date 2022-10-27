package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class RoomEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setEncryptionAlgorithm)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setEncryptionAlgorithm)
        }
    }

    internal suspend fun setEncryptionAlgorithm(event: Event<EncryptionEventContent>) {
        if (event is Event.StateEvent) {
            log.debug { "set encryption algorithm of room ${event.roomId}" }
            roomStore.update(event.roomId) { oldRoom ->
                oldRoom?.copy(
                    encryptionAlgorithm = event.content.algorithm,
                    membersLoaded = false // enforce all keys are loaded
                ) ?: Room(
                    roomId = event.roomId,
                    encryptionAlgorithm = event.content.algorithm,
                )
            }
        }
    }
}