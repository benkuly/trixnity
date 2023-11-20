package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
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
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
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
        api.sync.subscribe(Priority.AFTER_DEFAULT - 24_000, ::deleteLeftRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun updateRoomList(syncEvents: SyncEvents) = tm.writeTransaction {
        val rooms = syncEvents.syncResponse.room

        if (rooms != null) coroutineScope {
            val createEvents =
                async(start = CoroutineStart.LAZY) {
                    syncEvents
                        .filterContent<CreateEventContent>()
                        .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content } }
                        .toList()
                        .toMap()
                }
            val encryptionEnabled =
                async(start = CoroutineStart.LAZY) {
                    syncEvents
                        .filterContent<EncryptionEventContent>()
                        .mapNotNull { it.roomIdOrNull }
                        .toSet()
                }
            val nextRoomIds =
                async(start = CoroutineStart.LAZY) {
                    syncEvents
                        .filterContent<TombstoneEventContent>()
                        .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content.replacementRoom } }
                        .toList()
                        .toMap()
                }

            suspend fun Room?.mergeRoom(
                roomId: RoomId,
                membership: Membership,
                lastRelevantEvent: ClientEvent.RoomEvent<*>?,
                unreadMessageCount: Long?
            ): Room =
                (this ?: Room(roomId)).copy(
                    membership = membership,
                    createEventContent = createEvents.await()[roomId] ?: this?.createEventContent,
                    encrypted = encryptionEnabled.await().contains(roomId) || this?.encrypted == true,
                    lastRelevantEventId = lastRelevantEvent?.id ?: this?.lastRelevantEventId,
                    lastRelevantEventTimestamp = lastRelevantEvent?.originTimestamp
                        ?.let { Instant.fromEpochMilliseconds(it) } ?: this?.lastRelevantEventTimestamp,
                    unreadMessageCount = unreadMessageCount ?: this?.unreadMessageCount ?: 0,
                    nextRoomId = nextRoomIds.await()[roomId] ?: this?.nextRoomId,
                )
            rooms.join?.entries?.forEach { roomResponse ->
                val roomId = roomResponse.key
                val unreadMessageCount = roomResponse.value.unreadNotifications?.notificationCount
                val events = roomResponse.value.timeline?.events
                val lastRelevantEvent = events?.lastOrNull { config.lastRelevantEventFilter(it) }
                roomStore.update(roomId) { oldRoom ->
                    oldRoom.mergeRoom(
                        roomId = roomId,
                        membership = Membership.JOIN,
                        lastRelevantEvent = lastRelevantEvent,
                        unreadMessageCount = unreadMessageCount
                    )
                }
            }
            rooms.leave?.entries?.forEach { roomResponse ->
                val roomId = roomResponse.key
                val events = roomResponse.value.timeline?.events
                val lastRelevantEvent = events?.lastOrNull { config.lastRelevantEventFilter(it) }
                roomStore.update(roomId) { oldRoom ->
                    oldRoom.mergeRoom(
                        roomId = roomId,
                        membership = Membership.LEAVE,
                        lastRelevantEvent = lastRelevantEvent,
                        unreadMessageCount = null
                    )
                }
            }
            rooms.knock?.entries?.forEach { (roomId, _) ->
                roomStore.update(roomId) { oldRoom ->
                    oldRoom.mergeRoom(
                        roomId = roomId,
                        membership = Membership.KNOCK,
                        lastRelevantEvent = null,
                        unreadMessageCount = null
                    )
                }
            }
            rooms.invite?.entries?.forEach { (roomId, _) ->
                roomStore.update(roomId) { oldRoom ->
                    oldRoom.mergeRoom(
                        roomId = roomId,
                        membership = Membership.INVITE,
                        lastRelevantEvent = null,
                        unreadMessageCount = null
                    )
                }
            }
            createEvents.cancel()
            encryptionEnabled.cancel()
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