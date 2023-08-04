package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
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
        api.sync.subscribeFirstInSyncProcessing(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeLastInSyncProcessing(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) = tm.writeTransaction {
        syncResponse.filter<EncryptionEventContent>()
            .filterIsInstance<Event.StateEvent<EncryptionEventContent>>()
            .collect {
                setEncryptionAlgorithm(it)
            }
    }

    internal suspend fun setEncryptionAlgorithm(event: Event.StateEvent<EncryptionEventContent>) {
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