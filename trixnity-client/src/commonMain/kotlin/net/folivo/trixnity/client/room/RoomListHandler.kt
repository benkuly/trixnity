package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.MatrixClientConfiguration.DeleteRooms
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.MarkedUnreadEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.utils.ConcurrentList
import net.folivo.trixnity.utils.ConcurrentMap
import net.folivo.trixnity.utils.concurrentMutableList
import net.folivo.trixnity.utils.concurrentMutableMap
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val log = KotlinLogging.logger("net.folivo.trixnity.client.room.RoomListHandler")

class RoomListHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val forgetRoomService: ForgetRoomService,
    private val roomService: RoomService,
    private val userInfo: UserInfo,
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

        syncRooms?.join?.entries?.forEachParallel { (roomId, roomInfo) ->
            val mergeRoom = mergeRoom(
                roomId = roomId,
                membership = Membership.JOIN,
                lastRelevantEvent = roomInfo.timeline?.events?.lastOrNull { config.lastRelevantEventFilter(it) },
                summary = roomInfo.summary,
                createEventContent = roomInfo.findInTimelineOrState(),
                markedUnreadEventContent = roomInfo.findInAccountData(),
                encryptionEventContent = roomInfo.findInTimelineOrState(),
                tombstoneEventContent = roomInfo.findInTimelineOrState(),
                nameEventContent = roomInfo.findInTimelineOrState(),
                canonicalAliasEventContent = roomInfo.findInTimelineOrState(),
                readReceipts = (roomInfo.ephemeral?.events?.find { it.content is ReceiptEventContent }?.content as? ReceiptEventContent)
                    ?.events?.filterValues {
                        it[ReceiptType.Read]?.get(userInfo.userId) != null
                                || it[ReceiptType.PrivateRead]?.get(userInfo.userId) != null
                    }?.keys?.toSet(),
            )
            roomUpdates.add(roomId, mergeRoom)
        }
        syncRooms?.leave?.entries?.forEachParallel { (roomId, roomInfo) ->
            val mergeRoom = mergeRoom(
                roomId = roomId,
                membership = Membership.LEAVE,
                lastRelevantEvent = roomInfo.timeline?.events?.lastOrNull { config.lastRelevantEventFilter(it) },
                summary = null,
                createEventContent = roomInfo.findInTimelineOrState(),
                markedUnreadEventContent = roomInfo.findInAccountData(),
                encryptionEventContent = roomInfo.findInTimelineOrState(),
                tombstoneEventContent = roomInfo.findInTimelineOrState(),
                nameEventContent = roomInfo.findInTimelineOrState(),
                canonicalAliasEventContent = roomInfo.findInTimelineOrState(),
                readReceipts = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }
        syncRooms?.knock?.entries?.forEachParallel { (roomId, roomInfo) ->
            val mergeRoom = mergeRoom(
                roomId = roomId,
                membership = Membership.KNOCK,
                lastRelevantEvent = null,
                summary = null,
                createEventContent = roomInfo.findInState(),
                markedUnreadEventContent = null,
                encryptionEventContent = roomInfo.findInState(),
                tombstoneEventContent = roomInfo.findInState(),
                nameEventContent = roomInfo.findInState(),
                canonicalAliasEventContent = roomInfo.findInState(),
                readReceipts = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }
        syncRooms?.invite?.entries?.forEachParallel { (roomId, roomInfo) ->
            val mergeRoom = mergeRoom(
                roomId = roomId,
                membership = Membership.INVITE,
                lastRelevantEvent = null,
                summary = null,
                createEventContent = roomInfo.findInState(),
                markedUnreadEventContent = null,
                encryptionEventContent = roomInfo.findInState(),
                tombstoneEventContent = roomInfo.findInState(),
                nameEventContent = roomInfo.findInState(),
                canonicalAliasEventContent = roomInfo.findInState(),
                readReceipts = null,
            )
            roomUpdates.add(roomId) { mergeRoom(it) }
        }

        updateIsDirectAndAvatarUrls(syncEvents, roomUpdates)

        tm.writeTransaction {
            roomUpdates.read { toMap() }.forEach { (roomId, updates) ->
                roomStore.update(roomId) { oldRoom ->
                    updates.read { toList() }.fold(oldRoom) { room, update ->
                        update(room)
                    }
                }
            }
        }
    }

    private suspend fun mergeRoom(
        roomId: RoomId,
        membership: Membership,
        lastRelevantEvent: ClientEvent.RoomEvent<*>?,
        summary: Sync.Response.Rooms.JoinedRoom.RoomSummary?,
        createEventContent: CreateEventContent?,
        markedUnreadEventContent: MarkedUnreadEventContent?,
        encryptionEventContent: EncryptionEventContent?,
        tombstoneEventContent: TombstoneEventContent?,
        nameEventContent: NameEventContent?,
        canonicalAliasEventContent: CanonicalAliasEventContent?,
        readReceipts: Set<EventId>?,
    ): (Room?) -> Room {
        val markedAsUnread =
            (markedUnreadEventContent ?: roomAccountDataStore.get<MarkedUnreadEventContent>(roomId).first()?.content)
                ?.unread == true
        val lastRelevantEventTimestamp = lastRelevantEvent?.originTimestamp
            ?.let { Instant.fromEpochMilliseconds(it) }
        val name =
            calculateDisplayName(
                roomId = roomId,
                nameEventContent = nameEventContent,
                canonicalAliasEventContent = canonicalAliasEventContent,
                summary = summary,
            )
        return { oldRoom ->
            (oldRoom ?: Room(roomId = roomId)).copy(
                createEventContent = createEventContent ?: oldRoom?.createEventContent,
                membership = membership,
                encrypted = encryptionEventContent != null || oldRoom?.encrypted == true,
                lastRelevantEventId = lastRelevantEvent?.id ?: oldRoom?.lastRelevantEventId,
                lastRelevantEventTimestamp = lastRelevantEventTimestamp ?: oldRoom?.lastRelevantEventTimestamp,
                isUnread = markedAsUnread ||
                        (readReceipts?.contains(oldRoom?.lastEventId)?.not() ?: oldRoom?.isUnread) == true,
                nextRoomId = tombstoneEventContent?.replacementRoom ?: oldRoom?.nextRoomId,
                name = name ?: oldRoom?.name,
            )
        }
    }

    private suspend fun updateIsDirectAndAvatarUrls(
        syncEvents: SyncEvents,
        roomUpdates: ConcurrentMap<RoomId, ConcurrentList<(Room?) -> Room?>>,
    ) = coroutineScope {
        val directEvent = syncEvents.filterContent<DirectEventContent>().firstOrNull()?.content
        val syncRooms = syncEvents.syncResponse.room

        val allRooms by lazy {
            async {
                this@RoomListHandler.roomStore.getAll().first().keys +
                        syncRooms?.run {
                            this.join?.keys.orEmpty() +
                                    this.leave?.keys.orEmpty() +
                                    this.knock?.keys.orEmpty() +
                                    this.invite?.keys.orEmpty()
                        }.orEmpty()
            }
        }

        val allDirectRooms by lazy {
            async {
                (directEvent ?: this@RoomListHandler.globalAccountDataStore.get<DirectEventContent>().first()?.content)
                    ?.mapWithRoomIdKeys()
            }
        }

        val avatarEvents by lazy {
            async {
                syncEvents
                    .filterContent<AvatarEventContent>()
                    .mapNotNull { event -> event.roomIdOrNull?.let { it to event.content } }
                    .toList()
                    .toMap()
            }
        }

        val memberEvents by lazy {
            async {
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
        }

        coroutineScope {
            if (directEvent != null) {
                launch {
                    log.debug { "update all rooms isDirect" }
                    val allDirectEventRooms = directEvent.mapWithRoomIdKeys()
                    allRooms.await().forEach { roomId ->
                        val isDirect = allDirectEventRooms[roomId]?.first() != null
                        roomUpdates.add(roomId) { oldRoom ->
                            val membership = oldRoom?.membership
                            if (membership == Membership.LEAVE || membership == Membership.BAN) oldRoom
                            else oldRoom?.copy(isDirect = isDirect)
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
                            val membership = oldRoom?.membership
                            if (membership == Membership.LEAVE || membership == Membership.BAN) oldRoom
                            else oldRoom?.copy(avatarUrl = avatarUrl)
                        }
                    }
                }
            }
        }
    }

    internal suspend fun calculateDisplayName(
        roomId: RoomId,
        nameEventContent: NameEventContent? = null,
        canonicalAliasEventContent: CanonicalAliasEventContent? = null,
        summary: Sync.Response.Rooms.JoinedRoom.RoomSummary? = null,
    ): RoomDisplayName? {
        val oldSummary = roomStore.get(roomId).first()?.name?.summary

        if (nameEventContent == null && canonicalAliasEventContent == null && summary == oldSummary) return null

        val mergedRoomSummary =
            if (summary == null && oldSummary == null) null
            else Sync.Response.Rooms.JoinedRoom.RoomSummary(
                heroes = summary?.heroes ?: oldSummary?.heroes,
                joinedMemberCount = summary?.joinedMemberCount ?: oldSummary?.joinedMemberCount,
                invitedMemberCount = summary?.invitedMemberCount ?: oldSummary?.invitedMemberCount,
            )

        val nameFromNameEvent = (nameEventContent
            ?: roomStateStore.getByStateKey<NameEventContent>(roomId).first()?.content)?.name
        val nameFromAliasEvent = (canonicalAliasEventContent
            ?: roomStateStore.getByStateKey<CanonicalAliasEventContent>(roomId).first()?.content)?.alias?.full

        return when {
            nameFromNameEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromNameEvent, summary = mergedRoomSummary)

            nameFromAliasEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromAliasEvent, summary = mergedRoomSummary)

            else -> coroutineScope {
                val allMembers by lazy {
                    async {
                        (roomStateStore.get<MemberEventContent>(roomId).first() - userInfo.userId.full)
                            .mapNotNull { (key, value) -> value.first()?.content?.let { key to it } }
                    }
                }
                val joinedMembers by lazy {
                    async {
                        allMembers.await()
                            .filter { it.second.membership == Membership.INVITE || it.second.membership == Membership.JOIN }
                    }
                }
                val leftMembers by lazy {
                    async {
                        allMembers.await()
                            .filter { it.second.membership == Membership.BAN || it.second.membership == Membership.LEAVE }
                    }
                }
                val heroes = (mergedRoomSummary?.heroes?.let { it - userInfo.userId })
                    ?: joinedMembers.await().take(NUM_HEROES).map { UserId(it.first) }.takeIf { it.isNotEmpty() }
                    ?: leftMembers.await().take(NUM_HEROES).map { UserId(it.first) }

                val joinedMemberCount = kotlin.run {
                    val invitedMemberCount = mergedRoomSummary?.invitedMemberCount?.toInt() ?: 0
                    val joinedMemberCount = mergedRoomSummary?.joinedMemberCount?.toInt() ?: 0

                    if (invitedMemberCount == 0 && joinedMemberCount == 0) joinedMembers.await().size
                    else invitedMemberCount + joinedMemberCount
                }

                log.debug { "calculate room display name of $roomId (heroes=$heroes, joinedMemberCount=$joinedMemberCount" }
                val isEmpty = joinedMemberCount <= 1
                val otherUsersCount =
                    if (joinedMemberCount > 1 && (heroes.isEmpty() || heroes.size < joinedMemberCount - 1)) joinedMemberCount
                    else 0

                RoomDisplayName(
                    heroes = heroes,
                    otherUsersCount = otherUsersCount,
                    isEmpty = isEmpty,
                    summary = mergedRoomSummary
                )
            }
        }
    }

    internal suspend fun deleteLeftRooms(syncEvents: SyncEvents) {
        val syncLeaveRooms = syncEvents.syncResponse.room?.leave?.keys
        if (syncLeaveRooms != null) {
            if (config.deleteRooms is DeleteRooms.OnLeave) {
                val existingLeaveRooms = roomStore.getAll().first()
                    .filter { it.value.first()?.membership == Membership.LEAVE }
                    .keys

                if ((existingLeaveRooms - syncLeaveRooms).isNotEmpty()) {
                    log.warn { "there were LEAVE rooms which should have already been deleted (existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms)" }
                }

                val forgetRooms = existingLeaveRooms + syncLeaveRooms

                log.trace { "existingLeaveRooms=$existingLeaveRooms syncLeaveRooms=$syncLeaveRooms" }
                forgetRooms(forgetRooms)
            }

            if (config.deleteRooms is DeleteRooms.WhenNotJoined) {
                log.trace { "check rooms that were never joined" }
                findDeletedRoomsThatWereNeverJoined(syncLeaveRooms)
            }
        }
    }

    private suspend fun findDeletedRoomsThatWereNeverJoined(leave: Set<RoomId>) {
        val forgetRooms = leave.mapNotNull { roomId ->
            log.trace { "look into the timeline if we find any events that indicate that we were part of the room $roomId" }
            val hasTimelineEvents = roomService.getById(roomId).first()?.lastEventId?.let { lastEventId ->
                withTimeoutOrNull(5.seconds) {
                    roomService.getTimelineEvents(roomId, lastEventId).map { flow ->
                        val event = flow.firstOrNull()?.event
                        val content = event?.content
                        (event == null ||
                                event is ClientEvent.StateBaseEvent<*> &&
                                content is MemberEventContent &&
                                content.membership != Membership.JOIN &&
                                event.stateKey == userInfo.userId.full).not()
                    }.firstOrNull { it }
                }
            } ?: false
            if (hasTimelineEvents.not()) {
                log.debug { "delete room $roomId that was never joined" }
                roomId
            } else null
        }

        forgetRooms(forgetRooms)
    }

    private suspend fun forgetRooms(forgetRooms: Collection<RoomId>) {
        if (forgetRooms.isNotEmpty()) {
            log.debug { "forget rooms: $forgetRooms" }
            tm.writeTransaction {
                forgetRooms.forEach { roomId ->
                    forgetRoomService(roomId, false)
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

    private inline fun <reified T : RoomEventContent> Sync.Response.Rooms.JoinedRoom.findInTimelineOrState() =
        timeline?.events?.findLast { it.content is T }?.content as? T
            ?: state?.events?.findLast { it.content is T }?.content as? T

    private inline fun <reified T : RoomAccountDataEventContent> Sync.Response.Rooms.JoinedRoom.findInAccountData() =
        accountData?.events?.findLast { it.content is T }?.content as? T

    private inline fun <reified T : RoomEventContent> Sync.Response.Rooms.LeftRoom.findInTimelineOrState() =
        timeline?.events?.findLast { it.content is T }?.content as? T
            ?: state?.events?.findLast { it.content is T }?.content as? T

    private inline fun <reified T : RoomAccountDataEventContent> Sync.Response.Rooms.LeftRoom.findInAccountData() =
        accountData?.events?.findLast { it.content is T }?.content as? T

    private inline fun <reified T : RoomEventContent> Sync.Response.Rooms.KnockedRoom.findInState() =
        strippedState?.events?.findLast { it.content is T }?.content as? T

    private inline fun <reified T : RoomEventContent> Sync.Response.Rooms.InvitedRoom.findInState() =
        strippedState?.events?.findLast { it.content is T }?.content as? T

    companion object {
        private const val NUM_HEROES = 5
    }
}