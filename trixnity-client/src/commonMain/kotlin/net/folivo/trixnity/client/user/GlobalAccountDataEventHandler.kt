package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler

class GlobalAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::setGlobalAccountData)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::setGlobalAccountData)
        }
    }

    internal suspend fun setGlobalAccountData(syncProcessingData: SyncProcessingData) {
        val events = syncProcessingData.syncResponse.accountData?.events
        if (events?.isNotEmpty() == true)
            tm.writeTransaction {
                events.forEach { globalAccountDataStore.save(it) }
            }
    }
}