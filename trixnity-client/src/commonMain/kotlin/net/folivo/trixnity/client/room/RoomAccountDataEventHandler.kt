package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.RoomAccountDataStore
import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

class RoomAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val tm: TransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.STORE_EVENTS, subscriber = ::setRoomAccountData)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun setRoomAccountData(accountData: List<RoomAccountDataEvent<RoomAccountDataEventContent>>) {
        if (accountData.isNotEmpty())
            tm.transaction {
                accountData.forEach { roomAccountDataStore.save(it) }
            }
    }
}