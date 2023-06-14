package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val roomStateStore: RoomStateStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val roomUserStore: RoomUserStore,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse(::handleSyncResponse)
        api.sync.subscribeAfterSyncResponse(::handleAfterSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeSyncResponse(::handleSyncResponse)
            api.sync.unsubscribeAfterSyncResponse(::handleAfterSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        val rooms = syncResponse.room
        if (rooms != null) {
            rooms.join?.entries?.forEach { roomResponse ->
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
            rooms.leave?.entries?.forEach { roomResponse ->
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
            rooms.knock?.entries?.forEach { (roomId, _) ->
                roomStore.update(roomId) {
                    it?.copy(membership = Membership.KNOCK) ?: Room(
                        roomId,
                        membership = Membership.KNOCK
                    )
                }
            }
            rooms.invite?.entries?.forEach { (room, _) ->
                roomStore.update(room) {
                    it?.copy(membership = Membership.INVITE) ?: Room(
                        room,
                        membership = Membership.INVITE
                    )
                }
            }
        }
    }

    internal suspend fun handleAfterSyncResponse(syncResponse: Sync.Response) {
        val rooms = syncResponse.room
        if (rooms != null) {
            val allRooms =
                rooms.join?.keys.orEmpty() +
                        rooms.leave?.keys.orEmpty() +
                        rooms.knock?.keys.orEmpty() +
                        rooms.invite?.keys.orEmpty()
            val allExistingRooms = roomStore.getAll().value.keys

            val forgetRooms = allExistingRooms - allRooms

            log.trace { "allRooms=$allRooms allExistingRooms=$allExistingRooms" }
            if (forgetRooms.isNotEmpty()) {
                log.debug { "forget rooms: $forgetRooms" }
            }
            forgetRooms.forEach { roomId ->
                roomStore.delete(roomId)
                roomTimelineStore.deleteByRoomId(roomId)
                roomStateStore.deleteByRoomId(roomId)
                roomAccountDataStore.deleteByRoomId(roomId)
                roomUserStore.deleteByRoomId(roomId)
            }
        }
    }
}