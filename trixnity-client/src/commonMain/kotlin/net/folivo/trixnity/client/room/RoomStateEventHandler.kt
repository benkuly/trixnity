package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.StateEventContent

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeFirstInSyncProcessing(::setState)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeFirstInSyncProcessing(::setState)
        }
    }

    internal suspend fun setState(syncResponse: Sync.Response) = tm.writeTransaction {
        syncResponse.filter<StateEventContent>().collect {
            roomStateStore.save(it)
        }
    }
}