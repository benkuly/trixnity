package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomService: RoomService,
    private val tm: RepositoryTransactionManager,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.ROOM_LIST, ::updateRoomList).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT - 1, ::deleteLeftRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun updateRoomList(syncEvents: SyncEvents) = tm.writeTransaction {
        val rooms = syncEvents.syncResponse.room
        if (rooms != null) coroutineScope {
            val createEventContents =
                async(start = CoroutineStart.LAZY) {
                    syncEvents.filterContent<CreateEventContent>().toList()
                        .associateBy { it.roomIdOrNull }
                }
            rooms.join?.entries?.forEach { roomResponse ->
                val roomId = roomResponse.key
                val newUnreadMessageCount = roomResponse.value.unreadNotifications?.notificationCount
                val events = roomResponse.value.timeline?.events
                val lastRelevantEvent = events?.lastOrNull { config.lastRelevantEventFilter(it) }
                roomStore.update(roomId) { oldRoom ->
                    val room =
                        (oldRoom ?: Room(
                            roomId = roomId,
                            createEventContent = createEventContents.await()[roomId]?.content
                        ))
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
                    val room =
                        (oldRoom ?: Room(
                            roomId = roomId,
                            createEventContent = createEventContents.await()[roomId]?.content
                        ))
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
                        createEventContent = createEventContents.await()[roomId]?.content,
                        membership = Membership.KNOCK
                    )
                }
            }
            rooms.invite?.entries?.forEach { (roomId, _) ->
                roomStore.update(roomId) {
                    it?.copy(membership = Membership.INVITE) ?: Room(
                        roomId,
                        createEventContent = createEventContents.await()[roomId]?.content,
                        membership = Membership.INVITE
                    )
                }
            }
            createEventContents.cancel()
        }
    }

    internal suspend fun deleteLeftRooms(syncEvents: SyncEvents) {
        val syncLeaveRooms = syncEvents.syncResponse.room?.leave?.keys
        if (syncLeaveRooms != null && config.deleteRoomsOnLeave) {
            val existingLeaveRooms = roomStore.getAll().first()
                .filter { it.value.first()?.membership == Membership.LEAVE }
                .keys

            if ((existingLeaveRooms - syncLeaveRooms).isNotEmpty()) {
                log.warn { "there were LEAVE rooms which should have already been deleted (existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms)" }
            }

            val forgetRooms = existingLeaveRooms + syncLeaveRooms

            log.trace { "existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms" }
            if (forgetRooms.isNotEmpty()) {
                log.debug { "forget rooms: $forgetRooms" }
                tm.writeTransaction {
                    forgetRooms.forEach { roomId ->
                        roomService.forgetRoom(roomId)
                    }
                }
            }
        }
    }
}