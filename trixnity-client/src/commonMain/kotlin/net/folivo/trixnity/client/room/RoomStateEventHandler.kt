package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.StateEventContent

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::setState)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::setState)
        }
    }

    internal suspend fun setState(syncProcessingData: SyncProcessingData) {
        val stateEvents = syncProcessingData.allEvents.filterContent<StateEventContent>().toList()
        if (stateEvents.isNotEmpty())
            tm.writeTransaction {
                stateEvents.forEach {
                    roomStateStore.save(it)
                }
            }
    }
}