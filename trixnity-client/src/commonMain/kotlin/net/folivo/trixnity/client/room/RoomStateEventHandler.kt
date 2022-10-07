package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setState)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setState)
        }
    }

    internal suspend fun setState(event: Event<StateEventContent>) {
        roomStateStore.update(event)
    }
}