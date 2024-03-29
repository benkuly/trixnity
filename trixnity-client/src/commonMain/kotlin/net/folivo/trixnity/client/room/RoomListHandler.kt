package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.utils.ConcurrentList
import net.folivo.trixnity.utils.ConcurrentMap
import net.folivo.trixnity.utils.concurrentMutableList
import net.folivo.trixnity.utils.concurrentMutableMap

private val log = KotlinLogging.logger {}

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val forgetRoomService: ForgetRoomService,
    private val tm: TransactionManager,
    private val config: MatrixClientConfiguration,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.ROOM_LIST, ::updateRoomList).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT - 1, ::deleteLeftRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun updateRoomList(syncEvents: SyncEvents) = coroutineScope {
        val roomUpdates: ConcurrentMap<RoomId, ConcurrentList<(Room?) -> Room?>> = concurrentMutableMap()

        val syncRooms = syncEvents.syncResponse.room

        val createEvents =
            async(start = CoroutineStart.LAZY) {
                syncEvents
                    .filterContent<CreateEventContent>()
                    .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content } }
                    .toList()
                    .toMap()
            }
        val nameEvents =
            async(start = CoroutineStart.LAZY) {
                syncEvents
                    .filterContent<NameEventContent>()
                    .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content } }
                    .toList()
                    .toMap()
            }
        val canonicalAliasEvents =
            async(start = CoroutineStart.LAZY) {
                syncEvents
                    .filterContent<CanonicalAliasEventContent>()
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

        suspend fun createMergeRoom(
            roomId: RoomId,
            membership: Membership,
            lastRelevantEvent: ClientEvent.RoomEvent<*>?,
            unreadMessageCount: Long?,
            name: RoomDisplayName?,
        ): (Room?) -> Room {
            val createEventContent = createEvents.await()[roomId]
            val encrypted = encryptionEnabled.await().contains(roomId)
            val nextRoomId = nextRoomIds.await()[roomId]
            val lastRelevantEventTimestamp = lastRelevantEvent?.originTimestamp
                ?.let { Instant.fromEpochMilliseconds(it) }
            return { oldRoom ->
                (oldRoom ?: Room(roomId)).copy(
                    membership = membership,
                    createEventContent = createEventContent ?: oldRoom?.createEventContent,
                    encrypted = encrypted || oldRoom?.encrypted == true,
                    lastRelevantEventId = lastRelevantEvent?.id ?: oldRoom?.lastRelevantEventId,
                    lastRelevantEventTimestamp = lastRelevantEventTimestamp ?: oldRoom?.lastRelevantEventTimestamp,
                    unreadMessageCount = unreadMessageCount ?: oldRoom?.unreadMessageCount ?: 0,
                    nextRoomId = nextRoomId ?: oldRoom?.nextRoomId,
                    name = name ?: oldRoom?.name,
                )
            }
        }

        syncRooms?.join?.entries?.forEachParallel { (roomId, roomInfo) ->
            val unreadMessageCount = roomInfo.unreadNotifications?.notificationCount
            val lastRelevantEvent = roomInfo.timeline?.events?.lastOrNull { config.lastRelevantEventFilter(it) }
            val name = calculateDisplayName(
                roomId = roomId,
                nameEventContent = nameEvents.await()[roomId],
                canonicalAliasEventContent = canonicalAliasEvents.await()[roomId],
                roomSummary = roomInfo.summary
            )
            val mergeRoom = createMergeRoom(
                roomId = roomId,
                membership = Membership.JOIN,
                lastRelevantEvent = lastRelevantEvent,
                unreadMessageCount = unreadMessageCount,
                name = name,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }
        syncRooms?.leave?.entries?.forEachParallel { (roomId, roomInfo) ->
            val lastRelevantEvent = roomInfo.timeline?.events?.lastOrNull { config.lastRelevantEventFilter(it) }
            val mergeRoom = createMergeRoom(
                roomId = roomId,
                membership = Membership.LEAVE,
                lastRelevantEvent = lastRelevantEvent,
                unreadMessageCount = null,
                name = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }
        syncRooms?.knock?.entries?.forEachParallel { (roomId, _) ->
            val mergeRoom = createMergeRoom(
                roomId = roomId,
                membership = Membership.KNOCK,
                lastRelevantEvent = null,
                unreadMessageCount = null,
                name = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }
        syncRooms?.invite?.entries?.forEachParallel { (roomId, _) ->
            val mergeRoom = createMergeRoom(
                roomId = roomId,
                membership = Membership.INVITE,
                lastRelevantEvent = null,
                unreadMessageCount = null,
                name = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }

        updateIsDirectAndAvatarUrls(syncEvents, roomUpdates)

        tm.transaction {
            roomUpdates.read { toMap() }.forEach { (roomId, updates) ->
                roomStore.update(roomId) { oldRoom ->
                    updates.read { toList() }.fold(oldRoom) { room, update ->
                        update(room)
                    }
                }
            }
        }

        coroutineContext.cancelChildren()
    }

    private suspend fun updateIsDirectAndAvatarUrls(
        syncEvents: SyncEvents,
        roomUpdates: ConcurrentMap<RoomId, ConcurrentList<(Room?) -> Room?>>,
    ) = coroutineScope {
        val directEvent = syncEvents.filterContent<DirectEventContent>().firstOrNull()?.content
        val syncRooms = syncEvents.syncResponse.room

        val allRooms =
            async(start = CoroutineStart.LAZY) {
                this@RoomListHandler.roomStore.getAll().first().keys +
                        syncRooms?.run {
                            this.join?.keys.orEmpty() +
                                    this.leave?.keys.orEmpty() +
                                    this.knock?.keys.orEmpty() +
                                    this.invite?.keys.orEmpty()
                        }.orEmpty()
            }
        val allDirectRooms =
            async(start = CoroutineStart.LAZY) {
                (directEvent ?: this@RoomListHandler.globalAccountDataStore.get<DirectEventContent>().first()?.content)
                    ?.mapWithRoomIdKeys()
            }
        val avatarEvents =
            async(start = CoroutineStart.LAZY) {
                syncEvents
                    .filterContent<AvatarEventContent>()
                    .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content } }
                    .toList()
                    .toMap()
            }
        val memberEvents =
            async(start = CoroutineStart.LAZY) {
                syncEvents
                    .filterContent<MemberEventContent>()
                    .toList()
                    .groupBy { it.roomIdOrNull }
                    .mapNotNull { (key, value) -> if (key != null) key to value else null }
                    .toMap()
                    .mapValues { entry ->
                        entry.value.mapNotNull { event -> event.stateKeyOrNull?.let { it to event.content } }.toMap()
                    }
            }

        coroutineScope {
            if (directEvent != null) {
                launch {
                    log.debug { "update all rooms isDirect" }
                    val allDirectEventRooms = directEvent.mapWithRoomIdKeys()
                    allRooms.await().forEach { roomId ->
                        val isDirect = allDirectEventRooms[roomId]?.first() != null
                        roomUpdates.add(roomId) { oldRoom ->
                            oldRoom?.copy(isDirect = isDirect)
                        }
                    }
                }
            }

            val updateAvatarUrlRooms =
                if (directEvent != null) allRooms.await()
                else avatarEvents.await().keys +
                        memberEvents.await().keys.intersect(allDirectRooms.await()?.keys.orEmpty())

            if (updateAvatarUrlRooms.isNotEmpty()) {
                updateAvatarUrlRooms.forEach { roomId ->
                    launch {
                        log.debug { "update room avatar of room $roomId" }
                        val directUser = allDirectRooms.await()?.get(roomId)?.first()
                        val roomAvatarEvent =
                            avatarEvents.await()[roomId]
                                ?: roomStateStore.getByStateKey<AvatarEventContent>(roomId).first()?.content
                        val roomAvatarUrl = roomAvatarEvent?.url
                        val memberAvatarUrl =
                            if (directUser != null && roomAvatarUrl.isNullOrEmpty()) {
                                val memberEvent =
                                    memberEvents.await()[roomId]?.get(directUser.full)
                                        ?: roomStateStore.getByStateKey<MemberEventContent>(roomId, directUser.full)
                                            .first()?.content
                                memberEvent?.avatarUrl
                            } else null
                        val avatarUrl = roomAvatarUrl?.ifEmpty { null } ?: memberAvatarUrl
                        roomUpdates.add(roomId) { oldRoom ->
                            oldRoom?.copy(
                                avatarUrl = avatarUrl,
                            )
                        }
                    }
                }
            }
        }

        coroutineContext.cancelChildren()
    }

    internal suspend fun calculateDisplayName(
        roomId: RoomId,
        nameEventContent: NameEventContent? = null,
        canonicalAliasEventContent: CanonicalAliasEventContent? = null,
        roomSummary: Sync.Response.Rooms.JoinedRoom.RoomSummary? = null,
    ): RoomDisplayName? {
        val oldRoomSummary = roomStore.get(roomId).first()?.name?.summary

        if (nameEventContent == null && canonicalAliasEventContent == null && roomSummary == oldRoomSummary) return null

        val mergedRoomSummary =
            if (roomSummary == null && roomSummary == oldRoomSummary) null
            else Sync.Response.Rooms.JoinedRoom.RoomSummary(
                heroes = roomSummary?.heroes ?: oldRoomSummary?.heroes,
                joinedMemberCount = roomSummary?.joinedMemberCount ?: oldRoomSummary?.joinedMemberCount,
                invitedMemberCount = roomSummary?.invitedMemberCount ?: oldRoomSummary?.invitedMemberCount,
            )

        val nameFromNameEvent = (nameEventContent
            ?: roomStateStore.getByStateKey<NameEventContent>(roomId).first()?.content)?.name
        val nameFromAliasEvent = (canonicalAliasEventContent
            ?: roomStateStore.getByStateKey<CanonicalAliasEventContent>(roomId).first()?.content)?.alias?.full

        val roomName = when {
            nameFromNameEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromNameEvent, summary = mergedRoomSummary)

            nameFromAliasEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromAliasEvent, summary = mergedRoomSummary)

            else -> {
                val heroes = mergedRoomSummary?.heroes
                val joinedMemberCount = mergedRoomSummary?.joinedMemberCount
                val invitedMemberCount = mergedRoomSummary?.invitedMemberCount
                if (heroes == null || joinedMemberCount == null || invitedMemberCount == null) {
                    log.debug { "calculate room display name cancelled, because there are missing information (e.g. due to an invite)" }
                    return null
                }
                val us = 1

                log.debug { "calculate room display name of $roomId (heroes=$heroes, joinedMemberCount=$joinedMemberCount, invitedMemberCount=$invitedMemberCount)" }

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isEmpty() -> RoomDisplayName(isEmpty = true, summary = mergedRoomSummary)
                        else -> {
                            val isCompletelyEmpty = joinedMemberCount + invitedMemberCount <= 0
                            val leftMembersCount =
                                roomStateStore.membersCount(
                                    roomId,
                                    Membership.LEAVE,
                                    Membership.BAN
                                ) - if (isCompletelyEmpty) us else 0
                            when {
                                leftMembersCount <= heroes.size ->
                                    RoomDisplayName(
                                        isEmpty = true,
                                        summary = mergedRoomSummary
                                    )

                                else -> {
                                    RoomDisplayName(
                                        isEmpty = true,
                                        otherUsersCount = leftMembersCount - heroes.size,
                                        summary = mergedRoomSummary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    when {
                        //case ist not specified in the Spec, so this catches server misbehavior
                        heroes.isEmpty() ->
                            RoomDisplayName(
                                otherUsersCount = joinedMemberCount + invitedMemberCount - us,
                                summary = mergedRoomSummary
                            )

                        joinedMemberCount + invitedMemberCount - us <= heroes.size ->
                            RoomDisplayName(
                                summary = mergedRoomSummary
                            )

                        else ->
                            RoomDisplayName(
                                otherUsersCount = joinedMemberCount + invitedMemberCount - heroes.size - us,
                                summary = mergedRoomSummary
                            )
                    }
                }
            }
        }
        return roomName
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
                tm.transaction {
                    forgetRooms.forEach { roomId ->
                        forgetRoomService(roomId)
                    }
                }
            }
        }
    }

    private suspend fun <T> Collection<T>.forEachParallel(block: suspend (T) -> Unit) = coroutineScope {
        forEach { launch { block(it) } }
    }

    private suspend fun ConcurrentMap<RoomId, ConcurrentList<(Room?) -> Room?>>.add(
        roomId: RoomId,
        value: (Room?) -> Room?
    ) = write {
        getOrPut(roomId) { concurrentMutableList() }.write { add(value) }
    }

    private fun DirectEventContent.mapWithRoomIdKeys() =
        mappings.entries
            .flatMap { entry -> entry.value?.map { it to entry.key }.orEmpty() }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }
}