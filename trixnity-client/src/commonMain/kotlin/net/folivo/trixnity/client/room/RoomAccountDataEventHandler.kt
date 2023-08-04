package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomAccountDataStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler

class RoomAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeFirstInSyncProcessing(::setRoomAccountData)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeFirstInSyncProcessing(::setRoomAccountData)
        }
    }

    internal suspend fun setRoomAccountData(syncResponse: Sync.Response) = tm.writeTransaction {
        syncResponse.room?.join?.forEach { (_, joinedRoom) ->
            joinedRoom.accountData?.events?.forEach { roomAccountDataStore.save(it) }
        }
        syncResponse.room?.leave?.forEach { (_, leftRoom) ->
            leftRoom.accountData?.events?.forEach { roomAccountDataStore.save(it) }
        }
    }
}