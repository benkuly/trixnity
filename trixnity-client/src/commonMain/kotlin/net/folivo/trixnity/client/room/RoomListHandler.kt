package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.room.TimelineEventHandlerImpl.Companion.LAZY_LOAD_MEMBERS_FILTER
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeSyncResponse(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        syncResponse.room?.join?.entries?.forEach { roomResponse ->
            val roomId = roomResponse.key
            val newUnreadMessageCount = roomResponse.value.unreadNotifications?.notificationCount
            val events = roomResponse.value.timeline?.events
            val lastRelevantEventId = events?.lastOrNull { config.lastRelevantEventFilter(it) }?.id
            roomStore.update(roomId) {
                val room = (it ?: Room(roomId = roomId))
                room.copy(
                    membership = Membership.JOIN,
                    unreadMessageCount = newUnreadMessageCount ?: room.unreadMessageCount,
                    lastRelevantEventId = lastRelevantEventId ?: room.lastRelevantEventId
                )
            }
        }
        syncResponse.room?.leave?.entries?.forEach { roomResponse ->
            val roomId = roomResponse.key
            val events = roomResponse.value.timeline?.events
            val lastRelevantEventId = events?.lastOrNull { config.lastRelevantEventFilter(it) }?.id
            roomStore.update(roomId) {
                val room = (it ?: Room(roomId = roomId))
                room.copy(
                    membership = Membership.LEAVE,
                    lastRelevantEventId = lastRelevantEventId ?: room.lastRelevantEventId
                )
            }
        }
        syncResponse.room?.knock?.entries?.forEach { (roomId, _) ->
            roomStore.update(roomId) {
                it?.copy(membership = Membership.KNOCK) ?: Room(
                    roomId,
                    membership = Membership.KNOCK
                )
            }
        }
        syncResponse.room?.invite?.entries?.forEach { (room, _) ->
            roomStore.update(room) {
                it?.copy(membership = Membership.INVITE) ?: Room(
                    room,
                    membership = Membership.INVITE
                )
            }
        }
    }
}