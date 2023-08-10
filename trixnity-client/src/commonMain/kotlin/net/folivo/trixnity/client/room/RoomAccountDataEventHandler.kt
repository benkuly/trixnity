package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomAccountDataStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

class RoomAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::setRoomAccountData)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::setRoomAccountData)
        }
    }

    internal suspend fun setRoomAccountData(syncProcessingData: SyncProcessingData) {
        val accountData = syncProcessingData.allEvents
            .filter<RoomAccountDataEventContent, Event.RoomAccountDataEvent<RoomAccountDataEventContent>>()
            .toList()
        if (accountData.isNotEmpty())
            tm.writeTransaction {
                accountData.forEach { roomAccountDataStore.save(it) }
            }
    }
}