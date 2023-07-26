package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomService: RoomService,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse(::handleSyncResponse)
        api.sync.subscribeAfterSyncProcessing(::handleAfterSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribeSyncResponse(::handleSyncResponse)
            api.sync.unsubscribeAfterSyncProcessing(::handleAfterSyncResponse)
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
        val syncLeaveRooms = syncResponse.room?.leave?.keys
        if (syncLeaveRooms != null && config.deleteRoomsOnLeave) {
            val existingLeaveRooms = roomStore.getAll().value
                .filter { it.value.value?.membership == Membership.LEAVE }
                .keys

            if ((existingLeaveRooms - syncLeaveRooms).isNotEmpty()) {
                log.warn { "there were LEAVE rooms which should have already been deleted (existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms)" }
            }

            val forgetRooms = existingLeaveRooms + syncLeaveRooms

            log.trace { "existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms" }
            if (forgetRooms.isNotEmpty()) {
                log.debug { "forget rooms: $forgetRooms" }
            }
            forgetRooms.forEach { roomId ->
                roomService.forgetRoom(roomId)
            }
        }
    }
}