package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.unsubscribeOnCompletion

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
            tm.transaction {
                events.forEach { globalAccountDataStore.save(it) }
            }
    }
}