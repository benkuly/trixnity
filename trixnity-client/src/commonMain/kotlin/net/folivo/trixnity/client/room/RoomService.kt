package net.folivo.trixnity.client.room

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.rooms.Direction
import net.folivo.trixnity.client.api.sync.SyncApiClient
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.possiblyEncryptEvent
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.room.outbox.DefaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class RoomService(
    private val store: Store,
    private val api: MatrixApiClient,
    private val olm: OlmService,
    private val user: UserService,
    private val media: MediaService,
    private val setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    private val outboxMessageMediaUploaderMappings =
        DefaultOutboxMessageMediaUploaderMappings + customOutboxMessageMediaUploaderMappings

    suspend fun startEventHandling() = coroutineScope {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        launch(start = UNDISPATCHED) { api.sync.events<StateEventContent>().collect { store.roomState.update(it) } }
        launch(start = UNDISPATCHED) { api.sync.events<RoomAccountDataEventContent>().collect(::setRoomAccountData) }
        launch(start = UNDISPATCHED) { api.sync.events<EncryptionEventContent>().collect(::setEncryptionAlgorithm) }
        launch(start = UNDISPATCHED) { api.sync.events<MemberEventContent>().collect(::setOwnMembership) }
        launch(start = UNDISPATCHED) { api.sync.events<MemberEventContent>().collect(::setDirectRooms) }
        launch(start = UNDISPATCHED) { api.sync.events<RedactionEventContent>().collect(::redactTimelineEvent) }
        launch(start = UNDISPATCHED) { processOutboxMessages(store.roomOutboxMessage.getAll()) }
        launch(start = UNDISPATCHED) { api.sync.syncResponses.collect(::handleSyncResponse) }
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
                it.events?.lastOrNull()?.also { event -> setLastEventId(event) }
                it.events?.filterIsInstance<MessageEvent<*>>()?.lastOrNull()
                    ?.also { event -> setLastMessageEvent(event) }
                it.events?.forEach { event -> syncOutboxMessage(event) }
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
                it.events?.lastOrNull()?.let { event -> setLastEventId(event) }
            }
        }
    }

    internal suspend fun setRoomAccountData(accountDataEvent: Event<out RoomAccountDataEventContent>) {
        if (accountDataEvent is RoomAccountDataEvent) {
            store.roomAccountData.update(accountDataEvent)
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
                ?: Room(roomId = roomId, name = roomName)
        }
    }

    internal suspend fun setLastMessageEvent(event: MessageEvent<*>) {
        val eventTime = Instant.fromEpochMilliseconds(event.originTimestamp)
        store.room.update(event.roomId) { oldRoom ->
            oldRoom?.copy(lastMessageEventAt = eventTime, lastMessageEventId = event.id)
                ?: Room(roomId = event.roomId, lastMessageEventAt = eventTime, lastMessageEventId = event.id)
        }
    }

    internal suspend fun setLastEventId(event: Event<*>) {
        if (event is RoomEvent) {
            store.room.update(event.roomId) { oldRoom ->
                oldRoom?.copy(lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventId = event.id)
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
                )
            }
        }
    }

    internal suspend fun setDirectRooms(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        val ownUserId = store.account.userId.value
        if (roomId != null && stateKey != null && ownUserId != null) {
            val userId = UserId(stateKey)
            if (userId != ownUserId && event.content.isDirect == true) {
                val currentDirectRooms = store.globalAccountData.get<DirectEventContent>()?.content
                val existingDirectRoomsWithUser = currentDirectRooms?.mappings?.get(UserId(stateKey))
                val newDirectRooms = when {
                    existingDirectRoomsWithUser != null -> {
                        currentDirectRooms.copy(currentDirectRooms.mappings + (userId to (existingDirectRoomsWithUser + roomId)))
                    }
                    currentDirectRooms != null -> {
                        currentDirectRooms.copy(currentDirectRooms.mappings + (userId to setOf(roomId)))
                    }
                    else -> {
                        DirectEventContent(mapOf(userId to setOf(roomId)))
                    }
                }
                if (newDirectRooms != currentDirectRooms)
                    api.users.setAccountData(newDirectRooms, ownUserId)
            }
            if (userId == ownUserId && (event.content.membership == LEAVE || event.content.membership == BAN)) {
                val currentDirectRooms = store.globalAccountData.get<DirectEventContent>()?.content
                if (currentDirectRooms != null) {
                    val newDirectRooms = DirectEventContent(
                        currentDirectRooms.mappings.mapValues { it.value?.minus(roomId) }
                            .filterValues { !it.isNullOrEmpty() }
                    )
                    if (newDirectRooms != currentDirectRooms)
                        api.users.setAccountData(newDirectRooms, ownUserId)
                }
            }
        }
    }

    internal suspend fun setUnreadMessageCount(roomId: RoomId, count: Int) {
        store.room.update(roomId) { oldRoom ->
            oldRoom?.copy(
                unreadMessageCount = count
            ) ?: Room(
                roomId = roomId,
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
            log.debug { "add events to timeline at end of $roomId" }
            val room = store.room.get(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            val previousEventId =
                room.lastEventId?.also {
                    store.roomTimeline.update(it, roomId) { oldEvent ->
                        val nextEventIdForPreviousEvent = events[0].id
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

                val previousEvent = store.roomTimeline.getPrevious(startEvent)
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
                    log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
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
                    log.debug { "reached the start of visible timeline of $roomId" }
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
            val nextEvent = store.roomTimeline.getNext(startEvent)
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
                    log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
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
                            val decryptedEvent = kotlin.runCatching { olm.events.decryptMegolm(encryptedEvent) }
                            val verificationState =
                                if (decryptedEvent.isSuccess)
                                    olm.sign.verifyEncryptedMegolm(encryptedEvent)
                                else null
                            timelineEvent.copy(
                                decryptedEvent = decryptedEvent,
                                verificationState = verificationState
                            )
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

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getLastMessageEvent(
        roomId: RoomId,
        coroutineScope: CoroutineScope,
    ): StateFlow<StateFlow<TimelineEvent?>?> {
        return store.room.get(roomId).transformLatest { room ->
            if (room?.lastMessageEventId != null) emit(getTimelineEvent(room.lastMessageEventId, roomId, coroutineScope))
            else emit(null)
        }.stateIn(coroutineScope)
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

    internal suspend fun syncOutboxMessage(event: Event<*>) {
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
                                        val uploadedContent = uploader(content) { cacheUri ->
                                            media.uploadMedia(cacheUri, outboxMessage.mediaUploadProgress)
                                        }
                                        possiblyEncryptEvent(uploadedContent, roomId, store, olm, user)
                                    }
                                val eventId = api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId)
                                if (setOwnMessagesAsFullyRead) {
                                    api.rooms.setReadMarkers(roomId, eventId)
                                }
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

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<C?> {
        return store.roomAccountData.get(roomId, eventContentClass, scope)
            .map { it?.content }
            .stateIn(scope)
    }

    fun getOutbox(): StateFlow<List<RoomOutboxMessage>> = store.roomOutboxMessage.getAll()

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event<C>?> {
        return store.roomState.getByStateKey(roomId, stateKey, eventContentClass, scope)
    }
}