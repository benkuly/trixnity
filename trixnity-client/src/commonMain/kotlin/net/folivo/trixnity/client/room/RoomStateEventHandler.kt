package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler, LazyMemberEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncProcessingData: SyncProcessingData) {
        setState(syncProcessingData.allEvents.filterContent<StateEventContent>().toList())
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<Event<MemberEventContent>>) {
        setState(memberEvents.filterIsInstance<Event<StateEventContent>>(), skipWhenAlreadyPresent = true)
    }

    internal suspend fun setState(events: List<Event<StateEventContent>>, skipWhenAlreadyPresent: Boolean = false) {
        if (events.isNotEmpty())
            tm.writeTransaction {
                events.forEach {
                    if (skipWhenAlreadyPresent) {
                        roomStateStore
                    }
                    roomStateStore.save(it, skipWhenAlreadyPresent)
                }
            }
    }
}