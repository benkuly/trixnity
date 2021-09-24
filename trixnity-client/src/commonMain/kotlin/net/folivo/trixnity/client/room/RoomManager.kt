package net.folivo.trixnity.client.room

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.rooms.Direction
import net.folivo.trixnity.client.api.rooms.Membership
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getOriginTimestamp
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.coroutines.coroutineContext

class RoomManager(
    private val store: Store,
    private val api: MatrixApiClient,
    private val olm: OlmManager,
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    suspend fun startEventHandling() = coroutineScope {
        launch { api.sync.events<StateEventContent>().collect { store.rooms.state.update(it) } }
        launch { api.sync.events<EncryptionEventContent>().collect(::setEncryptionAlgorithm) }
        launch { api.sync.events<MemberEventContent>().collect(::setOwnMembership) }
        launch { api.sync.events<MemberEventContent>().collect(::setRoomUser) }
        launch { api.sync.events<RedactionEventContent>().collect(::redactTimelineEvent) }
        launch {
            api.sync.syncResponses.collect { syncResponse ->
                syncResponse.room?.join?.entries?.forEach { room ->
                    val roomId = room.key
                    // it is possible, that we didn't get our own JOIN event yet, because of lazy loading members
                    store.rooms.update(roomId) { oldRoom ->
                        oldRoom ?: Room(
                            roomId = roomId,
                            ownMembership = JOIN,
                            lastEventAt = fromEpochMilliseconds(0),
                            lastEventId = null
                        )
                    }
                    room.value.unreadNotifications?.notificationCount?.also { setUnreadMessageCount(roomId, it) }
                    room.value.timeline?.also {
                        addEventsToTimelineAtEnd(
                            roomId = roomId,
                            events = it.events,
                            previousBatch = it.previousBatch,
                            hasGapBefore = it.limited ?: false
                        )
                        it.events?.lastOrNull()?.also { event -> setLastEventAt(event) }
                    }

                    room.value.summary?.also {
                        setRoomDisplayName(
                            roomId,
                            it.heroes,
                            it.joinedMemberCount,
                            it.invitedMemberCount,
                        )
                    }
                }
                syncResponse.room?.leave?.entries?.forEach { room ->
                    room.value.timeline?.also {
                        addEventsToTimelineAtEnd(
                            roomId = room.key,
                            events = it.events,
                            previousBatch = it.previousBatch,
                            hasGapBefore = it.limited ?: false
                        )
                        it.events?.lastOrNull()?.let { event -> setLastEventAt(event) }
                    }
                }
            }
        }
        // TODO reaction and edit (also in fetchMissingEvents!)
    }

    private fun calculateUserDisplayName(
        displayName: String?,
        isUnique: Boolean,
        userId: UserId,
    ): String {
        return when {
            displayName.isNullOrEmpty() -> userId.full
            isUnique -> displayName
            else -> "$displayName (${userId.full})"
        }
    }

    private suspend fun resolveUserDisplayNameCollisions(
        displayName: String,
        isOld: Boolean,
        sourceUserId: UserId,
        roomId: RoomId
    ): Boolean {
        val usersWithSameDisplayName =
            store.rooms.users.byOriginalNameAndMembership(displayName, setOf(JOIN, INVITE), roomId) - sourceUserId
        if (usersWithSameDisplayName.size == 1) {
            val userId = usersWithSameDisplayName.first()
            val calculatedName = calculateUserDisplayName(displayName, isOld, userId)
            store.rooms.users.update(userId, roomId) {
                it?.copy(
                    name = calculatedName
                )
            }
            log.debug { "found displayName collision '$displayName' of $userId with $sourceUserId in $roomId - new displayName: '$calculatedName'" }
        }
        return usersWithSameDisplayName.isNotEmpty()
    }

    internal suspend fun setRoomUser(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val userId = UserId(stateKey)
            val membership = event.content.membership
            val newDisplayName = event.content.displayName

            val hasLeftRoom = membership == LEAVE || membership == BAN

            val oldDisplayName = store.rooms.users.byId(userId, roomId).value?.originalName
            val hasCollisions = if (hasLeftRoom || oldDisplayName != newDisplayName) {
                if (!oldDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(oldDisplayName, true, userId, roomId)
                if (!newDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(newDisplayName, hasLeftRoom, userId, roomId)
                else false
            } else false
            val calculatedName = calculateUserDisplayName(newDisplayName, !hasLeftRoom && !hasCollisions, userId)
            log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }

            store.rooms.users.update(userId, roomId) { oldRoomUser ->
                oldRoomUser?.copy(
                    name = calculatedName,
                    event = event
                ) ?: RoomUser(
                    roomId = roomId,
                    userId = userId,
                    name = calculatedName,
                    event = event
                )
            }
        }
    }

    internal suspend fun setRoomDisplayName(
        roomId: RoomId,
        heroes: List<String>?,
        joinedMemberCountFromSync: Int?,
        invitedMemberCountFromSync: Int?,
    ) {
        val nameFromNameEvent = store.rooms.state.byId<NameEventContent>(roomId).value?.content?.name
        val nameFromCanonicalAliasEvent =
            store.rooms.state.byId<CanonicalAliasEventContent>(roomId).value?.content?.alias

        val roomName = when {
            !nameFromNameEvent.isNullOrEmpty() -> RoomDisplayName(explicitName = nameFromNameEvent)
            nameFromCanonicalAliasEvent != null -> RoomDisplayName(explicitName = nameFromCanonicalAliasEvent.full)
            else -> {
                val joinedMemberCount = joinedMemberCountFromSync ?: store.rooms.state.membersCount(roomId, JOIN)
                val invitedMemberCount = invitedMemberCountFromSync ?: store.rooms.state.membersCount(roomId, INVITE)
                val us = 1

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isNullOrEmpty() -> RoomDisplayName(isEmpty = true)
                        else -> {
                            val isCompletelyEmpty = joinedMemberCount + invitedMemberCount <= 0
                            val leftMembersCount =
                                store.rooms.state.membersCount(roomId, LEAVE, BAN) - if (isCompletelyEmpty) us else 0
                            val heroesUserIds = heroes.map { UserId(it) }
                            when {
                                leftMembersCount <= heroes.size ->
                                    RoomDisplayName(
                                        isEmpty = true,
                                        heroes = heroesUserIds
                                    )
                                else -> {
                                    RoomDisplayName(
                                        isEmpty = true,
                                        heroes = heroesUserIds,
                                        otherUsersCount = leftMembersCount - heroes.size
                                    )
                                }
                            }
                        }
                    }
                } else {
                    when {
                        //case ist not specified in the Spec, so this catches server misbehavior
                        heroes.isNullOrEmpty() ->
                            RoomDisplayName(
                                otherUsersCount = joinedMemberCount + invitedMemberCount - us
                            )
                        else -> {
                            val heroesUserIds = heroes.map { UserId(it) }
                            when {
                                joinedMemberCount + invitedMemberCount - us <= heroes.size ->
                                    RoomDisplayName(
                                        heroes = heroesUserIds
                                    )
                                else ->
                                    RoomDisplayName(
                                        heroes = heroesUserIds,
                                        otherUsersCount = joinedMemberCount + invitedMemberCount - heroes.size - us
                                    )
                            }
                        }
                    }
                }
            }
        }
        store.rooms.update(roomId) { oldRoom ->
            oldRoom?.copy(name = roomName)
        }
    }


    internal suspend fun setLastEventAt(event: Event<*>) {
        val (roomId, eventTime) = when (event) {
            is MessageEvent -> event.roomId to fromEpochMilliseconds(event.originTimestamp)
            is StateEvent -> event.roomId to fromEpochMilliseconds(event.originTimestamp)
            else -> null to null
        }
        val eventId = event.getEventId()
        if (roomId != null && eventTime != null && eventId != null)
            store.rooms.update(roomId) { oldRoom ->
                oldRoom?.copy(lastEventAt = eventTime, lastEventId = eventId)
                    ?: Room(roomId = roomId, lastEventAt = eventTime, lastEventId = eventId)
            }
    }

    internal suspend fun setEncryptionAlgorithm(event: Event<EncryptionEventContent>) {
        if (event is StateEvent) {
            store.rooms.update(event.roomId) { oldRoom ->
                oldRoom?.copy(
                    encryptionAlgorithm = event.content.algorithm
                ) ?: Room(
                    roomId = event.roomId,
                    encryptionAlgorithm = event.content.algorithm,
                    lastEventAt = fromEpochMilliseconds(event.originTimestamp),
                    lastEventId = event.id
                )
            }
        }
    }

    internal suspend fun setOwnMembership(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null && stateKey == store.account.userId.value?.full) {
            store.rooms.update(roomId) { oldRoom ->
                oldRoom?.copy(
                    ownMembership = event.content.membership
                ) ?: Room(
                    roomId = roomId,
                    ownMembership = event.content.membership,
                    lastEventAt = fromEpochMilliseconds(event.getOriginTimestamp() ?: 0),
                    lastEventId = event.getEventId()
                )
            }
        }
    }

    internal suspend fun setUnreadMessageCount(roomId: RoomId, count: Int) {
        store.rooms.update(roomId) { oldRoom ->
            oldRoom?.copy(
                unreadMessageCount = count
            )
        }
    }

    internal suspend fun redactTimelineEvent(redactionEvent: Event<RedactionEventContent>) {
        if (redactionEvent is MessageEvent) {
            val roomId = redactionEvent.roomId
            log.debug { "redact event with id ${redactionEvent.content.redacts} in room $roomId" }
            store.rooms.timeline.update(redactionEvent.content.redacts, roomId) { oldTimelineEvent ->
                if (oldTimelineEvent != null) {
                    when (val oldEvent = oldTimelineEvent.event) {
                        is MessageEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.room
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            oldTimelineEvent.copy(
                                event = MessageEvent(
                                    RedactedMessageEventContent(eventType),
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedMessageEventData(
                                        redactedBecause = redactionEvent
                                    )
                                ),
                                decryptedEvent = null,
                            )
                        }
                        is StateEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.state
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            oldTimelineEvent.copy(
                                event = StateEvent(
                                    RedactedStateEventContent(eventType),
                                    oldEvent.id,
                                    oldEvent.sender,
                                    oldEvent.roomId,
                                    oldEvent.originTimestamp,
                                    UnsignedStateEventData(
                                        redactedBecause = redactionEvent
                                    ),
                                    oldEvent.stateKey,
                                ),
                                decryptedEvent = null,
                            )
                        }
                        else -> null
                    }
                } else null
            }
        }
    }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        events: List<RoomEvent<*>>?,
        previousBatch: String?,
        hasGapBefore: Boolean
    ) {
        if (!events.isNullOrEmpty()) {
            val nextEventIdForPreviousEvent = events[0].id
            val room = store.rooms.byId(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            val previousEvent =
                room.lastEventId?.let {
                    store.rooms.timeline.update(it, roomId) { oldEvent ->
                        if (hasGapBefore)
                            oldEvent?.copy(nextEventId = nextEventIdForPreviousEvent)
                        else {
                            val gap = oldEvent?.gap
                            oldEvent?.copy(
                                nextEventId = nextEventIdForPreviousEvent,
                                gap = if (gap is GapBoth) GapBefore(gap.batch) else null
                            )
                        }
                    }
                }
            val timelineEvents = events.mapIndexed { index, event ->
                when (index) {
                    events.lastIndex -> {
                        requireNotNull(previousBatch)
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = if (index == 0) previousEvent?.eventId
                            else events.getOrNull(index - 1)?.id,
                            nextEventId = null,
                            gap = if (index == 0 && hasGapBefore)
                                GapBoth(previousBatch)
                            else GapAfter(previousBatch),
                        )
                    }
                    0 -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = previousEvent?.eventId,
                            nextEventId = events.getOrNull(index + 1)?.id,
                            gap = if (hasGapBefore && previousBatch != null)
                                GapBefore(previousBatch)
                            else null
                        )
                    }
                    else -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = events.getOrNull(index - 1)?.id,
                            nextEventId = events.getOrNull(index + 1)?.id,
                            gap = null
                        )
                    }
                }
            }
            store.rooms.timeline.updateAll(timelineEvents)
        }
    }

    suspend fun fetchMissingEvents(startEvent: TimelineEvent, limit: Long = 20) {
        val startGap = startEvent.gap
        if (startGap != null) {
            val roomId = startEvent.roomId
            if (startGap is GapBefore || startGap is GapBoth) {
                log.debug { "fetch missing events before ${startEvent.eventId}" }

                val previousEvent = store.rooms.timeline.getPrevious(startEvent)?.value
                val destinationBatch = previousEvent?.gap?.batch
                val response = api.rooms.getEvents(
                    roomId = roomId,
                    from = startGap.batch,
                    to = destinationBatch,
                    dir = Direction.BACKWARDS,
                    limit = limit,
                    filter = """{"lazy_load_members":true}"""
                )
                val chunk = response.chunk
                if (!chunk.isNullOrEmpty()) {
                    val previousEventIndex =
                        previousEvent?.let { chunk.indexOfFirst { event -> event.id == it.eventId } }
                            ?: -1
                    val events = if (previousEventIndex < 0) chunk else chunk.take(previousEventIndex)
                    val filledGap = previousEventIndex >= 0 || response.end == destinationBatch
                    val timelineEvents = events.mapIndexed { index, event ->
                        val timelineEvent = when (index) {
                            events.lastIndex -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = previousEvent?.eventId,
                                    nextEventId = if (index == 0) startEvent.eventId
                                    else events.getOrNull(index - 1)?.id,
                                    gap = if (filledGap) null else GapBefore(response.end)
                                )
                            }
                            0 -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = events.getOrNull(index + 1)?.id,
                                    nextEventId = startEvent.eventId,
                                    gap = null
                                )
                            }
                            else -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = events.getOrNull(index + 1)?.id,
                                    nextEventId = events.getOrNull(index - 1)?.id,
                                    gap = null
                                )
                            }
                        }
                        if (index == 0)
                            store.rooms.timeline.update(startEvent.eventId, roomId) { oldStartEvent ->
                                val oldGap = oldStartEvent?.gap
                                oldStartEvent?.copy(
                                    previousEventId = event.id,
                                    gap = when (oldGap) {
                                        is GapAfter -> oldGap
                                        is GapBoth -> GapAfter(oldGap.batch)
                                        else -> null
                                    }
                                )
                            }
                        if (index == events.lastIndex && previousEvent != null)
                            store.rooms.timeline.update(previousEvent.eventId, roomId) { oldPreviousEvent ->
                                val oldGap = oldPreviousEvent?.gap
                                oldPreviousEvent?.copy(
                                    nextEventId = event.id,
                                    gap = when {
                                        filledGap && oldGap is GapBoth ->
                                            GapBefore(oldGap.batch)
                                        oldGap is GapBoth -> oldGap
                                        else -> null
                                    },
                                )
                            }
                        timelineEvent
                    }
                    store.rooms.timeline.updateAll(timelineEvents)
                }
            }
            val nextEvent = store.rooms.timeline.getNext(startEvent)?.value
            if (nextEvent != null && (startGap is GapAfter || startGap is GapBoth)) {
                log.debug { "fetch missing events after ${startEvent.eventId}" }

                val destinationBatch = nextEvent.gap?.batch

                val response = api.rooms.getEvents(
                    roomId = roomId,
                    from = startGap.batch,
                    to = destinationBatch,
                    dir = Direction.FORWARD,
                    limit = limit,
                    filter = """{"lazy_load_members":true}"""
                )
                val chunk = response.chunk
                if (!chunk.isNullOrEmpty()) {
                    val nextEventIndex = chunk.indexOfFirst { it.id == nextEvent.eventId }
                    val events = if (nextEventIndex < 0) chunk else chunk.take(nextEventIndex)
                    val filledGap = nextEventIndex >= 0 || response.end == destinationBatch
                    val timelineEvents = events.mapIndexed { index, event ->
                        val timelineEvent = when (index) {
                            events.lastIndex -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = if (index == 0) startEvent.eventId
                                    else events.getOrNull(index - 1)?.id,
                                    nextEventId = nextEvent.eventId,
                                    gap = if (filledGap) null else GapAfter(response.end),
                                )
                            }
                            0 -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = startEvent.eventId,
                                    nextEventId = events.getOrNull(index + 1)?.id,
                                    gap = null
                                )
                            }
                            else -> {
                                TimelineEvent(
                                    event = event,
                                    roomId = roomId,
                                    eventId = event.id,
                                    previousEventId = events.getOrNull(index - 1)?.id,
                                    nextEventId = events.getOrNull(index + 1)?.id,
                                    gap = null
                                )
                            }
                        }
                        if (index == 0)
                            store.rooms.timeline.update(startEvent.eventId, roomId) { oldStartEvent ->
                                val oldGap = oldStartEvent?.gap
                                oldStartEvent?.copy(
                                    nextEventId = event.id,
                                    gap = when (oldGap) {
                                        is GapBefore -> oldGap
                                        is GapBoth -> GapBefore(oldGap.batch)
                                        else -> null
                                    }
                                )
                            }
                        if (index == events.lastIndex)
                            store.rooms.timeline.update(nextEvent.eventId, roomId) { oldNextEvent ->
                                val oldGap = oldNextEvent?.gap
                                oldNextEvent?.copy(
                                    previousEventId = event.id,
                                    gap = when {
                                        filledGap && oldGap is GapBoth ->
                                            GapAfter(oldGap.batch)
                                        oldGap is GapBoth -> oldGap
                                        else -> null
                                    }
                                )
                            }
                        timelineEvent
                    }
                    store.rooms.timeline.updateAll(timelineEvents)
                }
            }
        }
    }

    suspend fun loadMembers(roomId: RoomId) {
        store.rooms.update(roomId) { oldRoom ->
            requireNotNull(oldRoom) { "cannot load members of a room, that we don't know yet ($roomId)" }
            if (!oldRoom.membersLoaded) {
                val memberEvents = api.rooms.getMembers(
                    roomId = roomId,
                    at = store.account.syncBatchToken.value,
                    notMembership = Membership.LEAVE
                ).toList()
                store.rooms.state.updateAll(memberEvents.filterIsInstance<StateEvent<StateEventContent>>())
                memberEvents.forEach { setRoomUser(it) }
                store.deviceKeys.outdatedKeys.update { it + memberEvents.map { event -> UserId(event.stateKey) } }
                oldRoom.copy(membersLoaded = true)
            } else oldRoom
        }
    }

    private val decryptionScope = CoroutineScope(Dispatchers.Default)

    private fun TimelineEvent.canBeDecrypted(): Boolean =
        this.event is MessageEvent
                && this.event.content is MegolmEncryptedEventContent
                && this.decryptedEvent == null

    @OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
    suspend fun getTimelineEvent(eventId: EventId, roomId: RoomId): StateFlow<TimelineEvent?> {
        return store.rooms.timeline.byId(eventId, roomId).also {
            val timelineEvent = it.value
            val content = timelineEvent?.event?.content
            if (timelineEvent?.canBeDecrypted() == true && content is MegolmEncryptedEventContent) {
                decryptionScope.launch(coroutineContext) {
                    log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                    store.olm.waitForInboundMegolmSession(roomId, content.sessionId, content.senderKey)
                    store.rooms.timeline.update(eventId, roomId) { oldEvent ->
                        if (oldEvent?.canBeDecrypted() == true) {
                            log.debug { "try to decrypt event $eventId in $roomId" }
                            @Suppress("UNCHECKED_CAST")
                            val encryptedEvent = oldEvent.event as MessageEvent<MegolmEncryptedEventContent>
                            timelineEvent.copy(
                                decryptedEvent = kotlin.runCatching { olm.events.decryptMegolm(encryptedEvent) })
                        } else oldEvent
                    }
                }
            }
        }
    }

    private
    val lastTimelineEventScope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getLastTimelineEvent(roomId: RoomId): StateFlow<StateFlow<TimelineEvent?>?> = coroutineScope {
        store.rooms.byId(roomId).transformLatest { room ->
            if (room?.lastEventId != null) emit(getTimelineEvent(room.lastEventId, roomId))
            else emit(null)
        }.stateIn(lastTimelineEventScope)
    }

    suspend fun getPreviousTimelineEvent(event: TimelineEvent): StateFlow<TimelineEvent?>? {
        return event.previousEventId?.let { getTimelineEvent(it, event.roomId) }
    }

    suspend fun getNextTimelineEvent(event: TimelineEvent): StateFlow<TimelineEvent?>? {
        return event.nextEventId?.let { getTimelineEvent(it, event.roomId) }
    }
}