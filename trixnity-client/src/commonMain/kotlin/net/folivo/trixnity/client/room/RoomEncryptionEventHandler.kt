package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent

private val log = KotlinLogging.logger {}

class RoomEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::handleSyncResponse, 99)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncProcessingData: SyncProcessingData) {
        setEncryptionAlgorithm(
            syncProcessingData.allEvents.filter<EncryptionEventContent, Event.StateEvent<EncryptionEventContent>>()
                .toList()
        )
    }

    internal suspend fun setEncryptionAlgorithm(events: List<Event.StateEvent<EncryptionEventContent>>) {
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