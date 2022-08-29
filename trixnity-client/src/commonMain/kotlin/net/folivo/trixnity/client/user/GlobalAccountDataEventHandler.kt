package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class GlobalAccountDataEventHandler(
    private val api: IMatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setGlobalAccountData)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setGlobalAccountData)
        }
    }

    internal suspend fun setGlobalAccountData(accountDataEvent: Event<GlobalAccountDataEventContent>) {
        if (accountDataEvent is Event.GlobalAccountDataEvent) {
            globalAccountDataStore.update(accountDataEvent)
        }
    }
}