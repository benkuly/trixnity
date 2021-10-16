package net.folivo.trixnity.client.room

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.rooms.Direction
import net.folivo.trixnity.client.api.rooms.Membership
import net.folivo.trixnity.client.api.sync.SyncApiClient
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.getEventId
import net.folivo.trixnity.client.getOriginTimestamp
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.media.MediaManager
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.room.outbox.DefaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class RoomManager(
    private val store: Store,
    private val api: MatrixApiClient,
    private val olm: OlmManager,
    private val media: MediaManager,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    private val outboxMessageMediaUploaderMappings =
        DefaultOutboxMessageMediaUploaderMappings + customOutboxMessageMediaUploaderMappings

    @OptIn(FlowPreview::class)
    suspend fun startEventHandling() = coroutineScope {
        launch { api.sync.events<StateEventContent>().collect { store.roomState.update(it) } }
        launch { api.sync.events<AccountDataEventContent>().collect(::setRoomAccountData) }
        launch { api.sync.events<EncryptionEventContent>().collect(::setEncryptionAlgorithm) }
        launch { api.sync.events<MemberEventContent>().collect(::setOwnMembership) }
        launch { api.sync.events<MemberEventContent>().collect(::setRoomUser) }
        launch { api.sync.events<RedactionEventContent>().collect(::redactTimelineEvent) }
        launch { api.sync.events<MessageEventContent>().collect(::syncOutboxMessage) }
        launch { processOutboxMessages(store.roomOutboxMessage.getAll()) }
        launch { api.sync.syncResponses.collect(::handleSyncResponse) }
        // TODO reaction and edit (also in fetchMissingEvents!)
    }

    // TODO test
    internal suspend fun handleSyncResponse(syncResponse: SyncResponse) {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            // it is possible, that we didn't get our own JOIN event yet, because of lazy loading members
            store.room.update(roomId) { oldRoom ->
                oldRoom ?: Room(
                    roomId = roomId,
                    membership = JOIN,
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
            store.roomUser.getByOriginalNameAndMembership(displayName, setOf(JOIN, INVITE), roomId) - sourceUserId
        if (usersWithSameDisplayName.size == 1) {
            val userId = usersWithSameDisplayName.first()
            val calculatedName = calculateUserDisplayName(displayName, isOld, userId)
            store.roomUser.update(userId, roomId) {
                it?.copy(name = calculatedName)
            }
            log.debug { "found displayName collision '$displayName' of $userId with $sourceUserId in $roomId - new displayName: '$calculatedName'" }
        }
        return usersWithSameDisplayName.isNotEmpty()
    }

    internal suspend fun setRoomAccountData(accountDataEvent: Event<out AccountDataEventContent>) {
        if (accountDataEvent is AccountDataEvent && accountDataEvent.roomId != null) {
            store.roomAccountData.update(accountDataEvent)
        }
    }

    internal suspend fun setRoomUser(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val userId = UserId(stateKey)
            val membership = event.content.membership
            val newDisplayName = event.content.displayName

            val hasLeftRoom = membership == LEAVE || membership == BAN

            val oldDisplayName = store.roomUser.get(userId, roomId)?.originalName
            val hasCollisions = if (hasLeftRoom || oldDisplayName != newDisplayName) {
                if (!oldDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(oldDisplayName, true, userId, roomId)
                if (!newDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(newDisplayName, hasLeftRoom, userId, roomId)
                else false
            } else false
            val calculatedName = calculateUserDisplayName(newDisplayName, !hasLeftRoom && !hasCollisions, userId)
            log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }

            store.roomUser.update(userId, roomId) { oldRoomUser ->
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
        heroes: List<UserId>?,
        joinedMemberCountFromSync: Int?,
        invitedMemberCountFromSync: Int?,
    ) {
        val nameFromNameEvent = store.roomState.getByStateKey<NameEventContent>(roomId)?.content?.name
        val nameFromCanonicalAliasEvent =
            store.roomState.getByStateKey<CanonicalAliasEventContent>(roomId)?.content?.alias

        val roomName = when {
            !nameFromNameEvent.isNullOrEmpty() -> RoomDisplayName(explicitName = nameFromNameEvent)
            nameFromCanonicalAliasEvent != null -> RoomDisplayName(explicitName = nameFromCanonicalAliasEvent.full)
            else -> {
                val joinedMemberCount = joinedMemberCountFromSync ?: store.roomState.membersCount(roomId, JOIN)
                val invitedMemberCount = invitedMemberCountFromSync ?: store.roomState.membersCount(roomId, INVITE)
                val us = 1

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isNullOrEmpty() -> RoomDisplayName(isEmpty = true)
                        else -> {
                            val isCompletelyEmpty = joinedMemberCount + invitedMemberCount <= 0
                            val leftMembersCount =
                                store.roomState.membersCount(roomId, LEAVE, BAN) - if (isCompletelyEmpty) us else 0
                            when {
                                leftMembersCount <= heroes.size ->
                                    RoomDisplayName(
                                        isEmpty = true,
                                        heroes = heroes
                                    )
                                else -> {
                                    RoomDisplayName(
                                        isEmpty = true,
                                        heroes = heroes,
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
                        joinedMemberCount + invitedMemberCount - us <= heroes.size ->
                            RoomDisplayName(
                                heroes = heroes
                            )
                        else ->
                            RoomDisplayName(
                                heroes = heroes,
                                otherUsersCount = joinedMemberCount + invitedMemberCount - heroes.size - us
                            )
                    }
                }
            }
        }
        store.room.update(roomId) { oldRoom ->
            oldRoom?.copy(name = roomName)
        }
    }


    internal suspend fun setLastEventAt(event: Event<*>) {
        if (event is RoomEvent) {
            val eventTime = fromEpochMilliseconds(event.originTimestamp)
            store.room.update(event.roomId) { oldRoom ->
                oldRoom?.copy(lastEventAt = eventTime, lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventAt = eventTime, lastEventId = event.id)
            }
        }
    }

    internal suspend fun setEncryptionAlgorithm(event: Event<EncryptionEventContent>) {
        if (event is StateEvent) {
            store.room.update(event.roomId) { oldRoom ->
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
            store.room.update(roomId) { oldRoom ->
                oldRoom?.copy(
                    membership = event.content.membership
                ) ?: Room(
                    roomId = roomId,
                    membership = event.content.membership,
                    lastEventAt = fromEpochMilliseconds(event.getOriginTimestamp() ?: 0),
                    lastEventId = event.getEventId()
                )
            }
        }
    }

    internal suspend fun setUnreadMessageCount(roomId: RoomId, count: Int) {
        store.room.update(roomId) { oldRoom ->
            oldRoom?.copy(
                unreadMessageCount = count
            )
        }
    }

    internal suspend fun redactTimelineEvent(redactionEvent: Event<RedactionEventContent>) {
        if (redactionEvent is MessageEvent) {
            val roomId = redactionEvent.roomId
            log.debug { "redact event with id ${redactionEvent.content.redacts} in room $roomId" }
            store.roomTimeline.update(redactionEvent.content.redacts, roomId) { oldTimelineEvent ->
                if (oldTimelineEvent != null) {
                    when (val oldEvent = oldTimelineEvent.event) {
                        is MessageEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.message
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
            val room = store.room.get(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            val previousEventId =
                room.lastEventId?.also {
                    store.roomTimeline.update(it, roomId) { oldEvent ->
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
                            previousEventId = if (index == 0) previousEventId
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
                            previousEventId = previousEventId,
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
            store.roomTimeline.addAll(timelineEvents)
        }
    }

    suspend fun fetchMissingEvents(startEvent: TimelineEvent, limit: Long = 20) {
        val startGap = startEvent.gap
        if (startGap != null) {
            val roomId = startEvent.roomId
            if (startGap is GapBefore || startGap is GapBoth) {
                log.debug { "fetch missing events before ${startEvent.eventId}" }

                val previousEvent = store.roomTimeline.getPrevious(startEvent, null)?.value
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
                val end = response.end
                if (!chunk.isNullOrEmpty()) {
                    val previousEventIndex =
                        previousEvent?.let { chunk.indexOfFirst { event -> event.id == it.eventId } } ?: -1
                    val events = if (previousEventIndex < 0) chunk else chunk.take(previousEventIndex)
                    val filledGap = previousEventIndex >= 0 || end == destinationBatch
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
                                    gap = if (filledGap) null else end?.let { GapBefore(it) }
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
                            store.roomTimeline.update(startEvent.eventId, roomId) { oldStartEvent ->
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
                            store.roomTimeline.update(previousEvent.eventId, roomId) { oldPreviousEvent ->
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
                    store.roomTimeline.addAll(timelineEvents)
                } else if (end == null || end == response.start) {
                    // we reached the start of visible timeline
                    store.roomTimeline.update(startEvent.eventId, roomId) { oldStartEvent ->
                        val oldGap = oldStartEvent?.gap
                        oldStartEvent?.copy(
                            gap = when (oldGap) {
                                is GapAfter -> oldGap
                                is GapBoth -> GapAfter(oldGap.batch)
                                else -> null
                            }
                        )
                    }
                }
            }
            val nextEvent = store.roomTimeline.getNext(startEvent, null)?.value
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
                                    gap = if (filledGap) null else response.end?.let { GapAfter(it) },
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
                            store.roomTimeline.update(startEvent.eventId, roomId) { oldStartEvent ->
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
                            store.roomTimeline.update(nextEvent.eventId, roomId) { oldNextEvent ->
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
                    store.roomTimeline.addAll(timelineEvents)
                }
            }
        }
    }

    suspend fun loadMembers(roomId: RoomId) {
        store.room.update(roomId) { oldRoom ->
            requireNotNull(oldRoom) { "cannot load members of a room, that we don't know yet ($roomId)" }
            if (!oldRoom.membersLoaded) {
                val memberEvents = api.rooms.getMembers(
                    roomId = roomId,
                    at = store.account.syncBatchToken.value,
                    notMembership = Membership.LEAVE
                ).toList()
                store.roomState.updateAll(memberEvents.filterIsInstance<Event<StateEventContent>>())
                memberEvents.forEach { setRoomUser(it) }
                store.deviceKeys.outdatedKeys.update { it + memberEvents.map { event -> UserId(event.stateKey) } }
                oldRoom.copy(membersLoaded = true)
            } else oldRoom
        }
    }

    private fun TimelineEvent.canBeDecrypted(): Boolean =
        this.event is MessageEvent
                && this.event.content is MegolmEncryptedEventContent
                && this.decryptedEvent == null

    @OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
    suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope
    ): StateFlow<TimelineEvent?> {
        return store.roomTimeline.get(eventId, roomId, coroutineScope).also {
            val timelineEvent = it.value
            val content = timelineEvent?.event?.content
            if (timelineEvent?.canBeDecrypted() == true && content is MegolmEncryptedEventContent) {
                coroutineScope.launch {
                    log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                    store.olm.waitForInboundMegolmSession(
                        roomId,
                        content.sessionId,
                        content.senderKey,
                        this
                    )
                    store.roomTimeline.update(eventId, roomId) { oldEvent ->
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

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getLastTimelineEvent(
        roomId: RoomId,
        coroutineScope: CoroutineScope
    ): StateFlow<StateFlow<TimelineEvent?>?> {
        return store.room.get(roomId).transformLatest { room ->
            if (room?.lastEventId != null) emit(getTimelineEvent(room.lastEventId, roomId, coroutineScope))
            else emit(null)
        }.stateIn(coroutineScope)
    }

    suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope
    ): StateFlow<TimelineEvent?>? {
        return event.previousEventId?.let { getTimelineEvent(it, event.roomId, coroutineScope) }
    }

    suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope
    ): StateFlow<TimelineEvent?>? {
        return event.nextEventId?.let { getTimelineEvent(it, event.roomId, coroutineScope) }
    }

    suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit) {
        val isEncryptedRoom = store.room.get(roomId).value?.encryptionAlgorithm == Megolm
        val content = MessageBuilder(isEncryptedRoom, media).build(builder)
        requireNotNull(content)
        store.roomOutboxMessage.add(
            RoomOutboxMessage(
                uuid4().toString(),
                roomId,
                content,
                false,
                MutableStateFlow(null)
            )
        )
    }

    internal suspend fun syncOutboxMessage(event: Event<MessageEventContent>) {
        if (event is MessageEvent && event.sender == store.account.userId.value) {
            event.unsigned?.transactionId?.also {
                store.roomOutboxMessage.deleteByTransactionId(it)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    internal suspend fun processOutboxMessages(outboxMessages: StateFlow<List<RoomOutboxMessage>>) = coroutineScope {
        val isConnected =
            api.sync.currentSyncState.map { it == SyncApiClient.SyncState.RUNNING }.stateIn(this)
        val schedule = Schedule.exponential<Throwable>(Duration.milliseconds(100))
            .or(Schedule.spaced(Duration.minutes(5)))
            .and(Schedule.doWhile { isConnected.value }) // just stop, when we are not connected anymore
            .logInput { log.debug { "failed sending outbox messages due to ${it.stackTraceToString()}" } }

        while (currentCoroutineContext().isActive) {
            isConnected.first { it } // just wait, until we are connected again
            try {
                schedule.retry {
                    log.info { "start sending outbox messages" }
                    outboxMessages.collect { outboxMessagesList ->
                        outboxMessagesList
                            .filter { !it.wasSent }
                            .forEach { outboxMessage ->
                                val roomId = outboxMessage.roomId
                                val content = outboxMessage.content
                                    .let { content ->
                                        val uploader =
                                            outboxMessageMediaUploaderMappings.find { it.kClass.isInstance(content) }?.uploader
                                                ?: throw IllegalArgumentException(
                                                    "EventContent class ${content::class.simpleName}} is not supported by any media uploader."
                                                )
                                        uploader(content) { cacheUri ->
                                            media.uploadMedia(cacheUri, outboxMessage.mediaUploadProgress)
                                        }
                                    }.let { content ->
                                        if (store.room.get(roomId).value?.encryptionAlgorithm == Megolm) {
                                            // The UI should do that, when a room gets opened, because of lazy loading
                                            // members Trixnity may not know all devices for encryption yet.
                                            // To ensure an easy usage of Trixnity and because
                                            // the impact on performance is minimal, we call it here for prevention.
                                            loadMembers(roomId)

                                            val megolmSettings =
                                                store.roomState.getByStateKey<EncryptionEventContent>(roomId)?.content
                                            requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
                                            olm.events.encryptMegolm(content, outboxMessage.roomId, megolmSettings)
                                        } else content
                                    }
                                api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId)
                                store.roomOutboxMessage.markAsSent(outboxMessage.transactionId)
                            }
                    }
                }
            } catch (error: Throwable) {
                log.info { "stop sending outbox messages, because we are not connected" }
            }
        }
    }

    fun getAll(): StateFlow<Set<Room>> {
        return store.room.getAll()
    }

    suspend fun getById(roomId: RoomId): StateFlow<Room?> {
        return store.room.get(roomId)
    }


    suspend fun <C : AccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<C?> {
        return store.roomAccountData.get(roomId, eventContentClass, scope)
            .map { it?.content }
            .stateIn(scope)
    }
}