package de.connect2x.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.unsubscribeOnCompletion

class GlobalAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val tm: TransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.STORE_EVENTS, subscriber = ::setGlobalAccountData).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setGlobalAccountData(syncEvents: SyncEvents) {
        val events = syncEvents.syncResponse.accountData?.events
        if (events?.isNotEmpty() == true)
            tm.writeTransaction {
                events.forEach { globalAccountDataStore.save(it) }
            }
    }
}