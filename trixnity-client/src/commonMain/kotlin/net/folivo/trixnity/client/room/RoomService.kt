package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.model.rooms.Direction
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.possiblyEncryptEvent
import net.folivo.trixnity.client.retryWhenSyncIsRunning
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
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger {}

class RoomService(
    private val store: Store,
    private val api: MatrixApiClient,
    private val olm: OlmService,
    private val user: UserService,
    private val media: MediaService,
    private val setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
) {
    private val outboxMessageMediaUploaderMappings =
        DefaultOutboxMessageMediaUploaderMappings + customOutboxMessageMediaUploaderMappings

    private val eventsInDecryption = MutableStateFlow(setOf<Pair<CoroutineScope, TimelineEvent>>())

    suspend fun start(scope: CoroutineScope) {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) { processOutboxMessages(store.roomOutboxMessage.getAll()) }
        api.sync.subscribeSyncResponse(::handleSyncResponse)
        api.sync.subscribe(::setRoomAccountData)
        api.sync.subscribe(::setEncryptionAlgorithm)
        api.sync.subscribe(::setOwnMembership)
        api.sync.subscribe(::setDirectRooms)
        api.sync.subscribe(::redactTimelineEvent)
        api.sync.subscribe<StateEventContent> { store.roomState.update(it) }
        api.sync.subscribe(::setRoomIsDirect)
        api.sync.subscribe(::setAvatarUrlForDirectRooms)
        api.sync.subscribe(::setAvatarUrlForMemberUpdates)
        api.sync.subscribe(::setAvatarUrlForAvatarEvents)
        api.sync.subscribeAfterSyncResponse(::removeOldOutboxMessages)
    }

    // TODO test
    internal suspend fun handleSyncResponse(syncResponse: SyncResponse) {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
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
                    newEvents = it.events,
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
            store.room.update(room.key) { oldRoom -> oldRoom ?: Room(room.key, membership = LEAVE) }
            room.value.timeline?.also {
                addEventsToTimelineAtEnd(
                    roomId = room.key,
                    newEvents = it.events,
                    previousBatch = it.previousBatch,
                    hasGapBefore = it.limited ?: false
                )
                it.events?.lastOrNull()?.let { event -> setLastEventId(event) }
            }
        }
        syncResponse.room?.knock?.entries?.forEach { (room, _) ->
            store.room.update(room) { oldRoom -> oldRoom ?: Room(room, membership = KNOCK) }
        }
        syncResponse.room?.invite?.entries?.forEach { (room, _) ->
            store.room.update(room) { oldRoom -> oldRoom ?: Room(room, membership = INVITE) }
        }
    }

    internal suspend fun setRoomAccountData(accountDataEvent: Event<RoomAccountDataEventContent>) {
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
        val sender = event.getSender()
        if (roomId != null && stateKey != null && ownUserId != null && sender != null) {
            val invitee = UserId(stateKey)
            val directUser = if (sender == ownUserId) {
                invitee
            } else if (invitee == ownUserId) {
                sender
            } else {
                return
            }
            if (event.content.isDirect == true) {
                val currentDirectRooms = store.globalAccountData.get<DirectEventContent>()?.content
                val existingDirectRoomsWithUser = currentDirectRooms?.mappings?.get(directUser)
                val newDirectRooms = when {
                    existingDirectRoomsWithUser != null -> {
                        currentDirectRooms.copy(currentDirectRooms.mappings + (directUser to (existingDirectRoomsWithUser + roomId)))
                    }
                    currentDirectRooms != null -> {
                        currentDirectRooms.copy(currentDirectRooms.mappings + (directUser to setOf(roomId)))
                    }
                    else -> {
                        DirectEventContent(mapOf(directUser to setOf(roomId)))
                    }
                }
                if (newDirectRooms != currentDirectRooms)
                    api.users.setAccountData(newDirectRooms, ownUserId)
            }
            if (directUser == ownUserId && (event.content.membership == LEAVE || event.content.membership == BAN)) {
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
                    }
                } else null
            }
        }
    }

    // You may think: wtf are you doing here? This prevents loops, when the server has the wonderful idea to send you
    // the same event in two different or in the same sync response(s). And this is actually happen 🤯.
    private suspend fun List<RoomEvent<*>>.filterDuplicateEvents() =
        this.distinctBy { it.id }.filter { store.roomTimeline.get(it.id, it.roomId) == null }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        newEvents: List<RoomEvent<*>>?,
        previousBatch: String?,
        hasGapBefore: Boolean
    ) = store.transaction {
        val events = newEvents?.filterDuplicateEvents()
        if (!events.isNullOrEmpty()) {
            log.debug { "add events to timeline at end of $roomId" }
            val room = store.room.get(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            val previousEventId =
                room.lastEventId?.also {
                    store.roomTimeline.update(it, roomId, withTransaction = false) { oldEvent ->
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
            val replaceOwnMessagesWithOutboxContent = timelineEvents.map {
                if (it.event.content is MegolmEncryptedEventContent) {
                    it.event.unsigned?.transactionId?.let { transactionId ->
                        store.roomOutboxMessage.getByTransactionId(transactionId)?.let { roomOutboxMessage ->
                            it.copy(decryptedEvent = Result.success(MegolmEvent(roomOutboxMessage.content, roomId)))
                        }
                    } ?: it
                } else it
            }
            store.roomTimeline.addAll(replaceOwnMessagesWithOutboxContent, withTransaction = false)
        }
    }

    suspend fun fetchMissingEvents(startEvent: TimelineEvent, limit: Long = 20): Result<Unit> = kotlin.runCatching {
        store.transaction {
            val startGap = startEvent.gap
            if (startGap != null) {
                val roomId = startEvent.roomId
                if (startGap is GapBefore || startGap is GapBoth) {
                    log.debug { "fetch missing events before ${startEvent.eventId}" }

                    val previousEvent = store.roomTimeline.getPrevious(startEvent, withTransaction = false)
                    val destinationBatch = previousEvent?.gap?.batch
                    val response = api.rooms.getEvents(
                        roomId = roomId,
                        from = startGap.batch,
                        to = destinationBatch,
                        dir = Direction.BACKWARDS,
                        limit = limit,
                        filter = """{"lazy_load_members":true}"""
                    )
                    val chunk = response.getOrThrow().chunk?.filterDuplicateEvents()
                    val end = response.getOrThrow().end
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
                                store.roomTimeline.update(
                                    startEvent.eventId,
                                    roomId,
                                    withTransaction = false
                                ) { oldStartEvent ->
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
                                store.roomTimeline.update(
                                    previousEvent.eventId,
                                    roomId,
                                    withTransaction = false
                                ) { oldPreviousEvent ->
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
                        store.roomTimeline.addAll(timelineEvents, withTransaction = false)
                    } else if (end == null || end == response.getOrThrow().start) {
                        log.debug { "reached the start of visible timeline of $roomId" }
                        store.roomTimeline.update(
                            startEvent.eventId,
                            roomId,
                            withTransaction = false
                        ) { oldStartEvent ->
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
                val nextEvent = store.roomTimeline.getNext(startEvent, withTransaction = false)
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
                    val chunk = response.getOrThrow().chunk?.filterDuplicateEvents()
                    if (!chunk.isNullOrEmpty()) {
                        log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
                        val nextEventIndex = chunk.indexOfFirst { it.id == nextEvent.eventId }
                        val events = if (nextEventIndex < 0) chunk else chunk.take(nextEventIndex)
                        val filledGap = nextEventIndex >= 0 || response.getOrThrow().end == destinationBatch
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
                                        gap = if (filledGap) null else response.getOrThrow().end?.let { GapAfter(it) },
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
                                store.roomTimeline.update(
                                    startEvent.eventId,
                                    roomId,
                                    withTransaction = false
                                ) { oldStartEvent ->
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
                                store.roomTimeline.update(
                                    nextEvent.eventId,
                                    roomId,
                                    withTransaction = false
                                ) { oldNextEvent ->
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
                        store.roomTimeline.addAll(timelineEvents, withTransaction = false)
                    }
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
                val origEventsInDecryption =
                    eventsInDecryption.getAndUpdate { events -> events + Pair(coroutineScope, timelineEvent) }
                if (origEventsInDecryption.contains(Pair(coroutineScope, timelineEvent))) return@also
                coroutineScope.launch {
                    log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                    store.olm.waitForInboundMegolmSession(
                        roomId,
                        content.sessionId,
                        content.senderKey,
                        this
                    )
                    store.roomTimeline.update(eventId, roomId, persistIntoRepository = false) { oldEvent ->
                        if (oldEvent?.canBeDecrypted() == true) {
                            log.debug { "try to decrypt event $eventId in $roomId" }
                            @Suppress("UNCHECKED_CAST")
                            val encryptedEvent = oldEvent.event as MessageEvent<MegolmEncryptedEventContent>
                            val decryptedEvent = kotlin.runCatching { olm.events.decryptMegolm(encryptedEvent) }
                            timelineEvent.copy(decryptedEvent = decryptedEvent)
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
            if (room?.lastMessageEventId != null) emit(
                getTimelineEvent(
                    room.lastMessageEventId,
                    roomId,
                    coroutineScope
                )
            )
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
                null,
                MutableStateFlow(null)
            )
        )
    }

    internal suspend fun syncOutboxMessage(event: Event<*>) {
        if (event is MessageEvent)
            if (event.sender == store.account.userId.value) {
                event.unsigned?.transactionId?.also {
                    store.roomOutboxMessage.deleteByTransactionId(it)
                }
            }
    }

    // we do this at the end of the sync, because it may be possible, that we missed events due to a gap
    @OptIn(ExperimentalTime::class)
    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = store.roomOutboxMessage.getAll().value
        outboxMessages.forEach {
            val deleteBeforeTimestamp = Clock.System.now() - 10.seconds
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.warn { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                store.roomOutboxMessage.deleteByTransactionId(it.transactionId)
            }
        }
    }

    internal suspend fun processOutboxMessages(outboxMessages: StateFlow<List<RoomOutboxMessage>>) = coroutineScope {
        api.sync.currentSyncState.retryWhenSyncIsRunning(
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
            scope = this
        ) {
            log.info { "start sending outbox messages" }
            outboxMessages.collect { outboxMessagesList ->
                outboxMessagesList
                    .filter { it.sentAt == null }
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
                                    media.uploadMedia(cacheUri, outboxMessage.mediaUploadProgress).getOrThrow()
                                }
                                possiblyEncryptEvent(uploadedContent, roomId, store, olm, user)
                            }
                        val eventId =
                            api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId).getOrThrow()
                        if (setOwnMessagesAsFullyRead) {
                            api.rooms.setReadMarkers(roomId, eventId).getOrThrow()
                        }
                        store.roomOutboxMessage.markAsSent(outboxMessage.transactionId)
                        log.debug { "sent message: $content" }
                    }
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
        key: String = "",
        scope: CoroutineScope
    ): StateFlow<C?> {
        return store.roomAccountData.get(roomId, eventContentClass, key, scope)
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

    internal suspend fun setRoomIsDirect(directEvent: Event<DirectEventContent>) {
        val allDirectRooms = directEvent.content.mappings.entries.flatMap { (_, rooms) ->
            rooms ?: emptySet()
        }.toSet()
        allDirectRooms.forEach { room -> store.room.update(room) { oldRoom -> oldRoom?.copy(isDirect = true) } }

        val allRooms = store.room.getAll().value.map { it.roomId }.toSet()
        allRooms.subtract(allDirectRooms)
            .forEach { room -> store.room.update(room) { oldRoom -> oldRoom?.copy(isDirect = false) } }
    }

    internal suspend fun setAvatarUrlForDirectRooms(directEvent: Event<DirectEventContent>) {
        directEvent.content.mappings.entries.forEach { (userId, rooms) ->
            rooms?.forEach { room ->
                if (store.roomState.getByStateKey<AvatarEventContent>(room)?.content?.url.isNullOrEmpty()) {
                    val avatarUrl =
                        store.roomState.getByStateKey<MemberEventContent>(
                            room,
                            stateKey = userId.full
                        )?.content?.avatarUrl
                    store.room.update(room) { oldRoom -> oldRoom?.copy(avatarUrl = avatarUrl?.ifEmpty { null }) }
                }
            }
        }
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvent: Event<MemberEventContent>) {
        memberEvent.getRoomId()?.let { roomId ->
            val room = store.room.get(roomId).value
            val ownUserId = store.account.userId.value
            if (room?.isDirect == true && ownUserId != memberEvent.getSender()) {
                store.room.update(roomId) { oldRoom ->
                    oldRoom?.copy(avatarUrl = memberEvent.content.avatarUrl?.ifEmpty { null })
                }
            }
        }
    }

    internal suspend fun setAvatarUrlForAvatarEvents(avatarEvent: Event<AvatarEventContent>) {
        avatarEvent.getRoomId()?.let { roomId ->
            val avatarUrl = avatarEvent.content.url
            val room = store.room.get(roomId).value
            if (room?.isDirect?.not() == true || avatarUrl.isNotEmpty()) {
                store.room.update(roomId) { oldRoom -> oldRoom?.copy(avatarUrl = avatarUrl.ifEmpty { null }) }
            } else if (avatarUrl.isEmpty()) {
                store.globalAccountData.get(DirectEventContent::class)?.content?.mappings?.let { mappings ->
                    mappings.entries.forEach { (userId, rooms) ->
                        rooms
                            ?.filter { room -> room == roomId }
                            ?.forEach { room ->
                                val newAvatarUrl =
                                    store.roomState.getByStateKey<MemberEventContent>(
                                        room,
                                        stateKey = userId.full
                                    )?.content?.avatarUrl
                                store.room.update(room) { oldRoom ->
                                    oldRoom?.copy(avatarUrl = newAvatarUrl?.ifEmpty { null })
                                }
                            }
                    }
                }
            }
        }
    }
}