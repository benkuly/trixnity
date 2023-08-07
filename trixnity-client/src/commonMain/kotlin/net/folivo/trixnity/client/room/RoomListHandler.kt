package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomService: RoomService,
    private val tm: RepositoryTransactionManager,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncResponse.subscribe(::updateRoomList, 100)
        api.sync.afterSyncResponse.subscribe(::deleteLeftRooms)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncResponse.unsubscribe(::updateRoomList)
            api.sync.afterSyncResponse.unsubscribe(::deleteLeftRooms)
        }
    }

    internal suspend fun updateRoomList(syncResponse: Sync.Response) = tm.writeTransaction {
        val rooms = syncResponse.room
        if (rooms != null) {
            rooms.join?.entries?.forEach { roomResponse ->
                val roomId = roomResponse.key
                val newUnreadMessageCount = roomResponse.value.unreadNotifications?.notificationCount
                val events = roomResponse.value.timeline?.events
                val lastRelevantEvent = events?.lastOrNull { config.lastRelevantEventFilter(it) }
                roomStore.update(roomId) { oldRoom ->
                    val room = (oldRoom ?: Room(roomId = roomId))
                    room.copy(
                        membership = Membership.JOIN,
                        unreadMessageCount = newUnreadMessageCount ?: room.unreadMessageCount,
                        lastRelevantEventId = lastRelevantEvent?.id ?: room.lastRelevantEventId,
                        lastRelevantEventTimestamp = lastRelevantEvent?.originTimestamp
                            ?.let { Instant.fromEpochMilliseconds(it) } ?: room.lastRelevantEventTimestamp,
                    )
                }
            }
            rooms.leave?.entries?.forEach { roomResponse ->
                val roomId = roomResponse.key
                val events = roomResponse.value.timeline?.events
                val lastRelevantEvent = events?.lastOrNull { config.lastRelevantEventFilter(it) }
                roomStore.update(roomId) { oldRoom ->
                    val room = (oldRoom ?: Room(roomId = roomId))
                    room.copy(
                        membership = Membership.LEAVE,
                        lastRelevantEventId = lastRelevantEvent?.id ?: room.lastRelevantEventId,
                        lastRelevantEventTimestamp = lastRelevantEvent?.originTimestamp
                            ?.let { Instant.fromEpochMilliseconds(it) } ?: room.lastRelevantEventTimestamp,
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

    internal suspend fun deleteLeftRooms(syncResponse: Sync.Response) = tm.writeTransaction {
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