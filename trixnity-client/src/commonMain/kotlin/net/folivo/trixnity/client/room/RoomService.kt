package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.client.api.model.rooms.Direction
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.api.model.sync.SyncResponse.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.room.outbox.DefaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.olm.OlmLibraryException
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class RoomService(
    private val ownUserId: UserId,
    private val store: Store,
    private val api: MatrixApiClient,
    private val olm: OlmService,
    private val key: KeyService,
    private val user: UserService,
    private val media: MediaService,
    private val setOwnMessagesAsFullyRead: Boolean = false,
    customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
) {
    private val outboxMessageMediaUploaderMappings =
        DefaultOutboxMessageMediaUploaderMappings + customOutboxMessageMediaUploaderMappings

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
        api.sync.subscribe(::setDirectEventContent)
        api.sync.subscribe(::setAvatarUrlForMemberUpdates)
        api.sync.subscribe(::setAvatarUrlForAvatarEvents)
        api.sync.subscribe(::setRoomDisplayNameFromNameEvent)
        api.sync.subscribe(::setRoomDisplayNameFromCanonicalAliasEvent)
        api.sync.subscribe(::setReadReceipts)
        api.sync.subscribeAfterSyncResponse(::removeOldOutboxMessages)
        api.sync.subscribeAfterSyncResponse(::handleSetRoomDisplayNamesQueue)
        api.sync.subscribeAfterSyncResponse(::handleDirectEventContent)
        api.sync.subscribeAfterSyncResponse(::setDirectRoomsAfterSync)
    }

    // TODO test
    internal suspend fun handleSyncResponse(syncResponse: SyncResponse) {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            store.room.update(roomId) { it?.copy(membership = JOIN) ?: Room(roomId = roomId, membership = JOIN) }
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
            room.value.summary?.also { roomSummary ->
                setRoomDisplayNamesQueue.update { it + (roomId to roomSummary) }
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            store.room.update(room.key) { it?.copy(membership = LEAVE) ?: Room(room.key, membership = LEAVE) }
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
            store.room.update(room) { it?.copy(membership = KNOCK) ?: Room(room, membership = KNOCK) }
        }
        syncResponse.room?.invite?.entries?.forEach { (room, _) ->
            store.room.update(room) { it?.copy(membership = INVITE) ?: Room(room, membership = INVITE) }
        }
    }

    internal suspend fun setRoomAccountData(accountDataEvent: Event<RoomAccountDataEventContent>) {
        if (accountDataEvent is RoomAccountDataEvent) {
            store.roomAccountData.update(accountDataEvent)
        }
    }

    internal fun setRoomDisplayNameFromNameEvent(event: Event<NameEventContent>) {
        val roomId = event.getRoomId()
        if (roomId != null) setRoomDisplayNamesQueue.update {
            if (it.containsKey(roomId)) it else it + (roomId to null)
        }
    }

    internal fun setRoomDisplayNameFromCanonicalAliasEvent(event: Event<CanonicalAliasEventContent>) {
        val roomId = event.getRoomId()
        if (roomId != null) setRoomDisplayNamesQueue.update {
            if (it.containsKey(roomId)) it else it + (roomId to null)
        }
    }

    private val setRoomDisplayNamesQueue = MutableStateFlow(mapOf<RoomId, RoomSummary?>())

    internal suspend fun handleSetRoomDisplayNamesQueue() {
        setRoomDisplayNamesQueue.value.forEach { (roomId, roomSummary) ->
            setRoomDisplayName(roomId, roomSummary)
        }
        setRoomDisplayNamesQueue.value = mapOf()
    }

    internal suspend fun setRoomDisplayName(
        roomId: RoomId,
        roomSummary: RoomSummary?,
    ) {
        val oldRoomSummary = store.room.get(roomId).value?.name?.summary

        val mergedRoomSummary = RoomSummary(
            heroes = roomSummary?.heroes ?: oldRoomSummary?.heroes,
            joinedMemberCount = roomSummary?.joinedMemberCount ?: oldRoomSummary?.joinedMemberCount,
            invitedMemberCount = roomSummary?.invitedMemberCount ?: oldRoomSummary?.invitedMemberCount,
        )

        val nameFromNameEvent = store.roomState.getByStateKey<NameEventContent>(roomId)?.content?.name
        val nameFromAliasEvent =
            store.roomState.getByStateKey<CanonicalAliasEventContent>(roomId)?.content?.alias?.full

        val roomName = when {
            nameFromNameEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromNameEvent, summary = mergedRoomSummary)
            nameFromAliasEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromAliasEvent, summary = mergedRoomSummary)
            else -> {
                val heroes = mergedRoomSummary.heroes
                val joinedMemberCount =
                    mergedRoomSummary.joinedMemberCount ?: store.roomState.membersCount(roomId, JOIN)
                val invitedMemberCount =
                    mergedRoomSummary.invitedMemberCount ?: store.roomState.membersCount(roomId, INVITE)
                val us = 1

                log.debug { "calculate room display name of $roomId (heroes=$heroes, joinedMemberCount=$joinedMemberCount, invitedMemberCount=$invitedMemberCount)" }

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isNullOrEmpty() -> RoomDisplayName(isEmpty = true, summary = mergedRoomSummary)
                        else -> {
                            val isCompletelyEmpty = joinedMemberCount + invitedMemberCount <= 0
                            val leftMembersCount =
                                store.roomState.membersCount(roomId, LEAVE, BAN) - if (isCompletelyEmpty) us else 0
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
                        heroes.isNullOrEmpty() ->
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
                    encryptionAlgorithm = event.content.algorithm,
                    membersLoaded = false // enforce all keys are loaded
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
        if (roomId != null && stateKey != null && stateKey == ownUserId.full) {
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

    private val setDirectRoomsEventContent = MutableStateFlow<DirectEventContent?>(null)

    internal suspend fun setDirectRooms(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        val sender = event.getSender()
        if (roomId != null && stateKey != null && sender != null) {
            val invitee = UserId(stateKey)
            val directUser =
                when (ownUserId) {
                    sender -> invitee
                    invitee -> sender
                    else -> return
                }

            if (directUser != ownUserId && event.content.isDirect == true) {
                log.debug { "mark room $roomId as direct room with $directUser" }
                val currentDirectRooms = setDirectRoomsEventContent.value
                    ?: store.globalAccountData.get<DirectEventContent>()?.content
                val existingDirectRoomsWithUser = currentDirectRooms?.mappings?.get(directUser) ?: setOf()
                val newDirectRooms =
                    currentDirectRooms?.copy(currentDirectRooms.mappings + (directUser to (existingDirectRoomsWithUser + roomId)))
                        ?: DirectEventContent(mapOf(directUser to setOf(roomId)))
                if (newDirectRooms != currentDirectRooms)
                    setDirectRoomsEventContent.value = newDirectRooms
            }
            if (directUser == ownUserId && (event.content.membership == LEAVE || event.content.membership == BAN)) {
                log.debug { "unmark room $roomId as direct room with $directUser" }
                val currentDirectRooms = setDirectRoomsEventContent.value
                    ?: store.globalAccountData.get<DirectEventContent>()?.content
                if (currentDirectRooms != null) {
                    val newDirectRooms = DirectEventContent(
                        currentDirectRooms.mappings.mapValues { it.value?.minus(roomId) }
                            .filterValues { it.isNullOrEmpty().not() }
                    )
                    if (newDirectRooms != currentDirectRooms)
                        setDirectRoomsEventContent.value = newDirectRooms
                }
            }
        }
    }

    internal suspend fun setDirectRoomsAfterSync() {
        val newDirectRooms = setDirectRoomsEventContent.value
        if (newDirectRooms != null && newDirectRooms != store.globalAccountData.get<DirectEventContent>()?.content)
            api.users.setAccountData(newDirectRooms, ownUserId)
        setDirectRoomsEventContent.value = null
    }

    // because DirectEventContent could be set before any rooms are in store
    private val directEventContent = MutableStateFlow<DirectEventContent?>(null)

    internal fun setDirectEventContent(directEvent: Event<DirectEventContent>) {
        directEventContent.value = directEvent.content
    }

    internal suspend fun handleDirectEventContent() {
        val content = directEventContent.value
        if (content != null) {
            setRoomIsDirect(content)
            setAvatarUrlForDirectRooms(content)
            directEventContent.value = null
        }
    }

    internal suspend fun setRoomIsDirect(directEventContent: DirectEventContent) {
        val allDirectRooms = directEventContent.mappings.entries.flatMap { (_, rooms) ->
            rooms ?: emptySet()
        }.toSet()
        allDirectRooms.forEach { room -> store.room.update(room) { oldRoom -> oldRoom?.copy(isDirect = true) } }

        val allRooms = store.room.getAll().value.keys
        allRooms.subtract(allDirectRooms)
            .forEach { room -> store.room.update(room) { oldRoom -> oldRoom?.copy(isDirect = false) } }
    }

    internal suspend fun setAvatarUrlForDirectRooms(directEventContent: DirectEventContent) {
        directEventContent.mappings.entries.forEach { (userId, rooms) ->
            rooms?.forEach { room ->
                if (store.roomState.getByStateKey<AvatarEventContent>(room)?.content?.url.isNullOrEmpty()) {
                    val avatarUrl = store.roomState.getByStateKey<MemberEventContent>(room, stateKey = userId.full)
                        ?.content?.avatarUrl
                    store.room.update(room) { oldRoom -> oldRoom?.copy(avatarUrl = avatarUrl?.ifEmpty { null }) }
                }
            }
        }
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvent: Event<MemberEventContent>) {
        memberEvent.getRoomId()?.let { roomId ->
            val room = store.room.get(roomId).value
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
                store.globalAccountData.get<DirectEventContent>()?.content?.mappings?.let { mappings ->
                    mappings.entries.forEach { (userId, rooms) ->
                        rooms
                            ?.filter { room -> room == roomId }
                            ?.forEach { room ->
                                val newAvatarUrl =
                                    store.roomState.getByStateKey<MemberEventContent>(room, stateKey = userId.full)
                                        ?.content?.avatarUrl
                                store.room.update(room) { oldRoom ->
                                    oldRoom?.copy(avatarUrl = newAvatarUrl?.ifEmpty { null })
                                }
                            }
                    }
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

    internal suspend fun setReadReceipts(receiptEvent: Event<ReceiptEventContent>) {
        receiptEvent.getRoomId()?.let { roomId ->
            receiptEvent.content.events.forEach { (eventId, receipts) ->
                receipts
                    .filterIsInstance<Receipt.ReadReceipt>()
                    .forEach { receipt ->
                        receipt.read.keys.forEach { userId ->
                            store.roomUser.update(userId, roomId) { oldRoomUser ->
                                oldRoomUser?.copy(lastReadMessage = eventId)
                            }
                        }
                    }
            }
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
                        store.roomOutboxMessage.get(transactionId)?.let { roomOutboxMessage ->
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
                    val session =
                        store.olm.getInboundMegolmSession(content.senderKey, content.sessionId, roomId, this@launch)
                    val firstKnownIndex = session.value?.firstKnownIndex
                    if (session.value == null) {
                        key.backup.loadMegolmSession(roomId, content.sessionId, content.senderKey)
                        log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                        store.olm.waitForInboundMegolmSession(
                            roomId, content.sessionId, content.senderKey, this@launch
                        )
                    }
                    log.trace { "try to decrypt event $eventId in $roomId" }
                    @Suppress("UNCHECKED_CAST")
                    val encryptedEvent = timelineEvent.event as MessageEvent<MegolmEncryptedEventContent>

                    val decryptEventAttempt = encryptedEvent.decryptCatching()
                    val exception = decryptEventAttempt.exceptionOrNull()
                    val decryptedEvent =
                        if (exception is OlmLibraryException && exception.message?.contains("UNKNOWN_MESSAGE_INDEX") == true
                            || exception is DecryptionException.SessionException && exception.cause.message?.contains("UNKNOWN_MESSAGE_INDEX") == true
                        ) {
                            key.backup.loadMegolmSession(roomId, content.sessionId, content.senderKey)
                            log.debug { "unknwon message index, so we start to wait for inbound megolm session to decrypt $eventId in $roomId again" }
                            store.olm.waitForInboundMegolmSession(
                                roomId,
                                content.sessionId,
                                content.senderKey,
                                this@launch,
                                firstKnownIndexLessThen = firstKnownIndex
                            )
                            encryptedEvent.decryptCatching()
                        } else decryptEventAttempt
                    store.roomTimeline.update(eventId, roomId, persistIntoRepository = false) { oldEvent ->
                        // we check here again, because an event could be redacted at the same time
                        if (oldEvent?.canBeDecrypted() == true) timelineEvent.copy(decryptedEvent = decryptedEvent)
                        else oldEvent
                    }
                }
            }
        }
    }

    private suspend fun MessageEvent<MegolmEncryptedEventContent>.decryptCatching(): Result<MegolmEvent<*>> {
        return try {
            Result.success(olm.events.decryptMegolm(this))
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            else Result.failure(ex)
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
            coroutineScope {
                if (room?.lastMessageEventId != null)
                    emit(
                        getTimelineEvent(
                            room.lastMessageEventId,
                            roomId,
                            this,
                        )
                    )
                else emit(null)
            }
        }.stateIn(coroutineScope)
    }

    suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit) {
        val isEncryptedRoom = store.room.get(roomId).value?.encryptionAlgorithm == Megolm
        val content = MessageBuilder(isEncryptedRoom, media).build(builder)
        requireNotNull(content)
        val transactionId = uuid4().toString()
        store.roomOutboxMessage.update(transactionId) {
            RoomOutboxMessage(
                transactionId = transactionId,
                roomId = roomId,
                content = content,
                sentAt = null,
                mediaUploadProgress = MutableStateFlow(null)
            )
        }
    }

    suspend fun abortSendMessage(transactionId: String) {
        store.roomOutboxMessage.update(transactionId) { null }
    }

    suspend fun retrySendMessage(transactionId: String) {
        store.roomOutboxMessage.update(transactionId) { it?.copy(retryCount = 0) }
    }

    internal suspend fun syncOutboxMessage(event: Event<*>) {
        if (event is MessageEvent)
            if (event.sender == ownUserId) {
                event.unsigned?.transactionId?.also {
                    store.roomOutboxMessage.update(it) { null }
                }
            }
    }

    // we do this at the end of the sync, because it may be possible, that we missed events due to a gap
    internal suspend fun removeOldOutboxMessages() {
        val outboxMessages = store.roomOutboxMessage.getAll().value
        outboxMessages.forEach {
            val deleteBeforeTimestamp = Clock.System.now() - 10.seconds
            if (it.sentAt != null && it.sentAt < deleteBeforeTimestamp) {
                log.warn { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                store.roomOutboxMessage.update(it.transactionId) { null }
            }
        }
    }

    internal suspend fun processOutboxMessages(outboxMessages: StateFlow<List<RoomOutboxMessage<*>>>) = coroutineScope {
        api.sync.currentSyncState.retryInfiniteWhenSyncIs(
            RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
            scope = this
        ) {
            log.info { "start sending outbox messages" }
            outboxMessages.collect { outboxMessagesList ->
                outboxMessagesList
                    .filter { it.sentAt == null && !it.reachedMaxRetryCount }
                    .forEach { outboxMessage ->
                        store.roomOutboxMessage.update(outboxMessage.transactionId) { it?.copy(retryCount = it.retryCount + 1) }
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
                        log.trace { "send to $roomId -> $content" }
                        val eventId =
                            api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId).getOrThrow()
                        if (setOwnMessagesAsFullyRead) {
                            api.rooms.setReadMarkers(roomId, eventId).getOrThrow()
                        }
                        store.roomOutboxMessage.update(outboxMessage.transactionId) { it?.copy(sentAt = Clock.System.now()) }
                        log.debug { "sent message: $content" }
                    }
            }
        }
    }

    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> = store.room.getAll()

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

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): C? {
        return store.roomAccountData.get(roomId, eventContentClass, key)?.content
    }

    fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> = store.roomOutboxMessage.getAll()

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event<C>?> {
        return store.roomState.getByStateKey(roomId, stateKey, eventContentClass, scope)
    }

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
    ): Event<C>? {
        return store.roomState.getByStateKey(roomId, stateKey, eventContentClass)
    }
}