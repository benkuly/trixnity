package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class RoomEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.ROOM_LIST - 1, subscriber = ::setEncryptionAlgorithm)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun setEncryptionAlgorithm(events: List<StateEvent<EncryptionEventContent>>) {
        if (events.isNotEmpty()) {
            tm.writeTransaction {
                events.forEach { event ->
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
    }
}