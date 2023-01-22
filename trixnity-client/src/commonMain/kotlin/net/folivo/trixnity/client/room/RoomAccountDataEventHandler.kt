package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomAccountDataStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

class RoomAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomAccountDataStore: RoomAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setRoomAccountData)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setRoomAccountData)
        }
    }

    internal suspend fun setRoomAccountData(accountDataEvent: Event<RoomAccountDataEventContent>) {
        if (accountDataEvent is Event.RoomAccountDataEvent) {
            roomAccountDataStore.save(accountDataEvent)
        }
    }
}