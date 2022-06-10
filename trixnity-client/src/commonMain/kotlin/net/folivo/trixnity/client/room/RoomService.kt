package net.folivo.trixnity.client.room

import com.benasher44.uuid.uuid4
import com.soywiz.korio.async.async
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.IOlmEventService
import net.folivo.trixnity.client.key.IKeyBackupService
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.room.outbox.DefaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.clientserverapi.client.AfterSyncResponseSubscriber
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.clientserverapi.model.sync.Sync
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
import net.folivo.trixnity.core.model.events.m.room.Membership.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.olm.OlmLibraryException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface IRoomService {
    suspend fun fetchMissingEvents(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    )

    suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE,
        fetchTimeout: Duration = INFINITE,
        fetchNeighborLimit: Long = 20,
    ): StateFlow<TimelineEvent?>

    suspend fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE
    ): Flow<StateFlow<TimelineEvent?>?>

    suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE
    ): StateFlow<TimelineEvent?>?

    suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration = INFINITE
    ): StateFlow<TimelineEvent?>?

    /**
     * Returns a flow of timeline events wrapped in a flow, which emits, when there is a new timeline event
     * at the end of the timeline.
     *
     * To convert it to a list, [toFlowList] can be used or e.g. the events can be consumed manually.
     *
     * The manual approach needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     */
    suspend fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction = BACKWARDS,
        decryptionTimeout: Duration = INFINITE
    ): Flow<StateFlow<TimelineEvent?>>

    /**
     * Returns the last timeline events as flow.
     *
     * To convert it to a list, [toFlowList] can be used or e.g. the events can be consumed manually:
     * ```kotlin
     * launch {
     *   matrixClient.room.getLastTimelineEvents(roomId).collectLatest { timelineEventsFlow ->
     *     timelineEventsFlow?.take(10)?.toList()?.reversed()?.forEach { println(it) }
     *   }
     * }
     * ```
     * The manual approach needs proper understanding of how flows work. For example: if the client is offline
     * and there are 5 timeline events in store, but `take(10)` is used, then `toList()` will suspend.
     */
    suspend fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration = INFINITE
    ): Flow<Flow<StateFlow<TimelineEvent?>>?>

    /**
     * Returns all timeline events from the moment this method is called. This also triggers decryption for each timeline event.
     *
     * It is possible, that the matrix server does not send all timeline events.
     * These gaps in the timeline are not filled automatically. Gap filling is available in
     * [getTimelineEvents] and [getLastTimelineEvents].
     *
     * @param syncResponseBufferSize the size of the buffer for consuming the sync response. When set to 0, the sync will
     * be suspended until all events from the sync response are consumed. This could prevent decryption, because keys may
     * be received in a later sync response.
     */
    fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration = 30.seconds,
        syncResponseBufferSize: Int = 10,
    ): Flow<TimelineEvent>

    suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit)

    suspend fun abortSendMessage(transactionId: String)

    suspend fun retrySendMessage(transactionId: String)
    fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>>

    suspend fun getById(roomId: RoomId): StateFlow<Room?>

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): Flow<C?>

    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String = "",
    ): C?

    fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>>

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Event<C>?>

    suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String = "",
        eventContentClass: KClass<C>,
    ): Event<C>?
}

class RoomService(
    private val ownUserId: UserId,
    private val store: Store,
    private val api: MatrixClientServerApiClient,
    private val olmEvent: IOlmEventService,
    private val keyBackup: IKeyBackupService,
    private val user: IUserService,
    private val media: IMediaService,
    private val currentSyncState: StateFlow<SyncState>,
    private val config: MatrixClientConfiguration,
    private val scope: CoroutineScope,
) : IRoomService {
    companion object {
        const val LAZY_LOAD_MEMBERS_FILTER = """{"lazy_load_members":true}"""
    }

    private val outboxMessageMediaUploaderMappings =
        DefaultOutboxMessageMediaUploaderMappings + config.customOutboxMessageMediaUploaderMappings

    init {
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
        api.sync.subscribeAfterSyncResponse { removeOldOutboxMessages() }
        api.sync.subscribeAfterSyncResponse { handleSetRoomDisplayNamesQueue() }
        api.sync.subscribeAfterSyncResponse { handleDirectEventContent() }
        api.sync.subscribeAfterSyncResponse { setDirectRoomsAfterSync() }
    }

    // TODO test
    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            store.room.update(roomId) { it?.copy(membership = JOIN) ?: Room(roomId = roomId, membership = JOIN) }
            room.value.unreadNotifications?.notificationCount?.also { setUnreadMessageCount(roomId, it) }
            room.value.timeline?.also {
                withRoomTimelineMutexAndTransaction(room.key) {
                    addEventsToTimelineAtEnd(
                        roomId = roomId,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.also { event -> setLastEventId(event) }
                    it.events?.forEach { event ->
                        syncOutboxMessage(event)
                        setLastRelevantEvent(event)
                    }
                }
            }
            room.value.summary?.also { roomSummary ->
                setRoomDisplayNamesQueue.update { it + (roomId to roomSummary) }
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            store.room.update(room.key) { it?.copy(membership = LEAVE) ?: Room(room.key, membership = LEAVE) }
            room.value.timeline?.also {
                withRoomTimelineMutexAndTransaction(room.key) {
                    addEventsToTimelineAtEnd(
                        roomId = room.key,
                        newEvents = it.events,
                        previousBatch = it.previousBatch,
                        nextBatch = syncResponse.nextBatch,
                        hasGapBefore = it.limited ?: false
                    )
                    it.events?.lastOrNull()?.let { event -> setLastEventId(event) }
                    it.events?.forEach { event -> setLastRelevantEvent(event) }
                }
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

    private val setRoomDisplayNamesQueue =
        MutableStateFlow(mapOf<RoomId, Sync.Response.Rooms.JoinedRoom.RoomSummary?>())

    internal suspend fun handleSetRoomDisplayNamesQueue() {
        setRoomDisplayNamesQueue.value.forEach { (roomId, roomSummary) ->
            setRoomDisplayName(roomId, roomSummary)
        }
        setRoomDisplayNamesQueue.value = mapOf()
    }

    internal suspend fun setRoomDisplayName(
        roomId: RoomId,
        roomSummary: Sync.Response.Rooms.JoinedRoom.RoomSummary?,
    ) {
        val oldRoomSummary = store.room.get(roomId).value?.name?.summary

        val mergedRoomSummary = Sync.Response.Rooms.JoinedRoom.RoomSummary(
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

    internal suspend fun setLastEventId(event: Event<*>) {
        if (event is RoomEvent) {
            store.room.update(event.roomId, withTransaction = false) { oldRoom ->
                oldRoom?.copy(lastEventId = event.id)
                    ?: Room(roomId = event.roomId, lastEventId = event.id)
            }
        }
    }

    internal suspend fun setLastRelevantEvent(event: RoomEvent<*>) {
        if (config.lastRelevantEventFilter(event))
            store.room.update(event.roomId, withTransaction = false) { oldRoom ->
                oldRoom?.copy(lastRelevantEventId = event.id)
                    ?: Room(roomId = event.roomId, lastRelevantEventId = event.id)
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
            val userWithMembershipChange = UserId(stateKey)
            val directUser =
                when {
                    ownUserId == sender -> userWithMembershipChange
                    ownUserId == userWithMembershipChange -> sender
                    sender == userWithMembershipChange -> sender
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
                setDirectRoomsEventContent.value = newDirectRooms
            }
            if (event.content.membership == LEAVE || event.content.membership == BAN) {
                if (directUser != ownUserId) {
                    log.debug { "unmark room $roomId as direct room with $directUser" }
                    val currentDirectRooms = setDirectRoomsEventContent.value
                        ?: store.globalAccountData.get<DirectEventContent>()?.content
                    if (currentDirectRooms != null) {
                        val newDirectRooms = DirectEventContent(
                            (currentDirectRooms.mappings + (directUser to (currentDirectRooms.mappings[directUser].orEmpty() - roomId)))
                                .filterValues { it.isNullOrEmpty().not() }
                        )
                        setDirectRoomsEventContent.value = newDirectRooms
                    }
                } else {
                    log.debug { "remove room $roomId from direct rooms, because we left it" }
                    val currentDirectRooms = setDirectRoomsEventContent.value
                        ?: store.globalAccountData.get<DirectEventContent>()?.content
                    if (currentDirectRooms != null) {
                        val newDirectRooms = DirectEventContent(
                            currentDirectRooms.mappings.mapValues { it.value?.minus(roomId) }
                                .filterValues { it.isNullOrEmpty().not() }
                        )
                        setDirectRoomsEventContent.value = newDirectRooms
                    }
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
            if (room?.isDirect == true && ownUserId.full != memberEvent.getStateKey()) {
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

    internal suspend fun setUnreadMessageCount(roomId: RoomId, count: Long) {
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
                                content = null,
                            )
                        }
                        is StateEvent -> {
                            val eventType =
                                api.eventContentSerializerMappings.state
                                    .find { it.kClass.isInstance(oldEvent.content) }?.type
                                    ?: "UNKNOWN"
                            oldTimelineEvent.copy(
                                event = StateEvent(
                                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.2/rooms/v9/#redactions
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
                                content = null,
                            )
                        }
                    }
                } else null
            }
        }
    }

    private val timelineMutex = MutableStateFlow<Map<RoomId, Mutex>>(emptyMap())
    private suspend fun <T : Any> withRoomTimelineMutexAndTransaction(roomId: RoomId, block: suspend () -> T): T =
        requireNotNull(timelineMutex.updateAndGet { if (it.containsKey(roomId)) it else it + (roomId to Mutex()) }[roomId])
            .withLock {
                log.trace { "lock $roomId" }
                store.transaction { block() }.also { log.trace { "unlock $roomId" } }
            }

    // You may think: wtf are you doing here? This prevents loops, when the server has the wonderful idea to send you
    // the same event in two different or in the same sync response(s). And that really happens 🤯.
    private suspend fun List<RoomEvent<*>>.filterDuplicateAndExistingEvents() =
        this.distinctBy { it.id }.filter { store.roomTimeline.get(it.id, it.roomId, withTransaction = false) == null }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        newEvents: List<RoomEvent<*>>?,
        previousBatch: String?,
        nextBatch: String,
        hasGapBefore: Boolean
    ) {
        val events = newEvents?.filterDuplicateAndExistingEvents()
        if (!events.isNullOrEmpty()) {
            log.debug { "add events to timeline at end of $roomId" }
            val room = store.room.get(roomId).value
            requireNotNull(room) { "cannot update timeline of a room, that we don't know yet ($roomId)" }
            suspend fun useDecryptedOutboxMessagesForOwnTimelineEvents(timelineEvents: List<TimelineEvent>) =
                timelineEvents.map {
                    if (it.event.isEncrypted) {
                        it.event.unsigned?.transactionId?.let { transactionId ->
                            store.roomOutboxMessage.get(transactionId)?.let { roomOutboxMessage ->
                                it.copy(content = Result.success(roomOutboxMessage.content))
                            }
                        } ?: it
                    } else it
                }
            addEventsToTimeline(
                startEvent = TimelineEvent(
                    event = events.first(),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null
                ),
                roomId = roomId,
                previousToken = previousBatch,
                previousHasGap = hasGapBefore,
                previousEvent = room.lastEventId?.let { store.roomTimeline.get(it, roomId, withTransaction = false) },
                previousEventChunk = null,
                nextToken = nextBatch,
                nextHasGap = true,
                nextEvent = null,
                nextEventChunk = events.drop(1),
                modifyTimelineEventsBeforeSave = ::useDecryptedOutboxMessagesForOwnTimelineEvents
            )
        }
    }

    override suspend fun fetchMissingEvents(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long
    ) {
        scope.async {
            currentSyncState.retryWhenSyncIs(
                RUNNING,
                onError = { log.error(it) { "could not fetch missing event $startEventId" } },
            ) {
                internalFetchMissingEvents(startEventId, roomId, limit).getOrThrow()
            }
        }.await()
    }

    private suspend fun internalFetchMissingEvents(
        startEventId: EventId,
        roomId: RoomId,
        limit: Long = 20
    ): Result<Unit> = withRoomTimelineMutexAndTransaction(roomId) {
        kotlin.runCatching {
            val isLastEventId = store.room.get(roomId).value?.lastEventId == startEventId

            val initialStartEvent = store.roomTimeline.get(startEventId, roomId, withTransaction = false)
            val startEvent: TimelineEvent
            val previousToken: String?
            val previousHasGap: Boolean
            val previousEvent: TimelineEvent?
            val previousEventChunk: List<RoomEvent<*>>?
            val nextToken: String?
            val nextHasGap: Boolean
            val nextEvent: TimelineEvent?
            val nextEventChunk: List<RoomEvent<*>>?

            if (initialStartEvent == null) {
                log.debug { "fetch missing event from $startEventId" }
                val context = api.rooms.getEventContext(
                    roomId = roomId,
                    eventId = startEventId,
                    filter = LAZY_LOAD_MEMBERS_FILTER,
                    limit = if (isLastEventId) 0 else limit
                ).getOrThrow()

                previousToken = context.start
                previousEvent = context.eventsBefore
                    ?.map { store.roomTimeline.get(it.id, it.roomId, withTransaction = false) }
                    ?.find { it?.gap?.hasGapAfter == true }
                previousEventChunk = context.eventsBefore?.filterDuplicateAndExistingEvents()
                previousHasGap = previousEvent == null

                nextToken = context.end
                nextEvent = context.eventsAfter
                    ?.map { store.roomTimeline.get(it.id, it.roomId, withTransaction = false) }
                    ?.find { it?.gap?.hasGapBefore == true }
                nextEventChunk = context.eventsAfter?.filterDuplicateAndExistingEvents()
                nextHasGap = nextEvent == null

                startEvent = TimelineEvent(
                    event = context.event,
                    previousEventId = null,
                    nextEventId = null,
                    gap = null
                )
            } else {
                startEvent = initialStartEvent
                val startGap = startEvent.gap
                val startGapBatchBefore = startGap?.batchBefore
                val startGapBatchAfter = startGap?.batchAfter

                val possiblyPreviousEvent = store.roomTimeline.getPrevious(startEvent)
                if (startGapBatchBefore != null) {
                    log.debug { "fetch missing events before $startEventId" }
                    val destinationBatch = possiblyPreviousEvent?.gap?.batchAfter
                    val response = api.rooms.getEvents(
                        roomId = roomId,
                        from = startGapBatchBefore,
                        to = destinationBatch,
                        dir = BACKWARDS,
                        limit = limit,
                        filter = LAZY_LOAD_MEMBERS_FILTER
                    ).getOrThrow()
                    previousToken = response.end?.takeIf { it != response.start } // detects start of timeline
                    previousEvent = possiblyPreviousEvent
                        ?: response.chunk
                            ?.map { store.roomTimeline.get(it.id, it.roomId, withTransaction = false) }
                            ?.find { it?.gap?.hasGapAfter == true }
                    previousEventChunk = response.chunk?.filterDuplicateAndExistingEvents()
                    previousHasGap = response.end != destinationBatch
                            && response.chunk?.none { it.id == previousEvent?.eventId } == true
                } else {
                    previousToken = null
                    previousEvent = possiblyPreviousEvent
                    previousEventChunk = null
                    previousHasGap = false
                }

                val possiblyNextEvent = store.roomTimeline.getNext(startEvent)
                if (startGapBatchAfter != null && !isLastEventId) {
                    log.debug { "fetch missing events after $startEventId" }
                    val destinationBatch = possiblyNextEvent?.gap?.batchBefore
                    val response = api.rooms.getEvents(
                        roomId = roomId,
                        from = startGapBatchAfter,
                        to = destinationBatch,
                        dir = FORWARDS,
                        limit = limit,
                        filter = LAZY_LOAD_MEMBERS_FILTER
                    ).getOrThrow()
                    nextToken = response.end
                    nextEvent = possiblyNextEvent
                        ?: response.chunk
                            ?.map { store.roomTimeline.get(it.id, it.roomId, withTransaction = false) }
                            ?.find { it?.gap?.hasGapBefore == true }
                    nextEventChunk = response.chunk?.filterDuplicateAndExistingEvents()
                    nextHasGap = response.end != destinationBatch
                            && response.chunk?.none { it.id == nextEvent?.eventId } == true
                } else {
                    nextToken = startGapBatchAfter
                    nextEvent = possiblyNextEvent
                    nextEventChunk = null
                    nextHasGap = isLastEventId
                }
            }
            addEventsToTimeline(
                startEvent = startEvent,
                roomId = roomId,
                previousToken = previousToken,
                previousHasGap = previousHasGap,
                previousEvent = previousEvent,
                previousEventChunk = previousEventChunk,
                nextToken = nextToken,
                nextHasGap = nextHasGap,
                nextEvent = nextEvent,
                nextEventChunk = nextEventChunk,
            )
        }
    }

    private suspend fun addEventsToTimeline(
        startEvent: TimelineEvent,
        roomId: RoomId,
        previousToken: String?,
        previousHasGap: Boolean,
        previousEvent: TimelineEvent?,
        previousEventChunk: List<RoomEvent<*>>?,
        nextToken: String?,
        nextHasGap: Boolean,
        nextEvent: TimelineEvent?,
        nextEventChunk: List<RoomEvent<*>>?,
        modifyTimelineEventsBeforeSave: suspend (List<TimelineEvent>) -> List<TimelineEvent> = { it }
    ) {
        log.trace {
            "addEventsToTimeline with parameters:\n" +
                    "startEvent=${startEvent.eventId}\n" +
                    "previousToken=$previousToken, previousHasGap=$previousHasGap, previousEvent=${previousEvent?.eventId}, previousEventChunk=${previousEventChunk?.map { it.id }}\n" +
                    "nextToken=$nextToken, nextHasGap=$nextHasGap, nextEvent=${nextEvent?.eventId}, nextEventChunk=${nextEventChunk?.map { it.id }}"
        }

        if (previousEvent != null)
            store.roomTimeline.update(previousEvent.eventId, roomId, withTransaction = false) { oldPreviousEvent ->
                val oldGap = oldPreviousEvent?.gap
                oldPreviousEvent?.copy(
                    nextEventId = previousEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                    gap = if (previousHasGap) oldGap else oldGap?.removeGapAfter(),
                )?.let { modifyTimelineEventsBeforeSave(listOf(it)).first() }
            }
        if (nextEvent != null)
            store.roomTimeline.update(nextEvent.eventId, roomId, withTransaction = false) { oldNextEvent ->
                val oldGap = oldNextEvent?.gap
                oldNextEvent?.copy(
                    previousEventId = nextEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                    gap = if (nextHasGap) oldGap else oldGap?.removeGapBefore()
                )?.let { modifyTimelineEventsBeforeSave(listOf(it)).first() }
            }
        store.roomTimeline.update(startEvent.eventId, roomId, withTransaction = false) { oldStartEvent ->
            val hasGapBefore = previousEventChunk.isNullOrEmpty() && previousHasGap
            val hasGapAfter = nextEventChunk.isNullOrEmpty() && nextHasGap
            (oldStartEvent ?: startEvent).copy(
                previousEventId = previousEventChunk?.firstOrNull()?.id ?: previousEvent?.eventId,
                nextEventId = nextEventChunk?.firstOrNull()?.id ?: nextEvent?.eventId,
                gap = when {
                    hasGapBefore && hasGapAfter && previousToken != null && nextToken != null
                    -> GapBoth(previousToken, nextToken)
                    hasGapBefore && previousToken != null -> GapBefore(previousToken)
                    hasGapAfter && nextToken != null -> GapAfter(nextToken)
                    else -> null
                }
            ).let { modifyTimelineEventsBeforeSave(listOf(it)).first() }
        }

        if (!previousEventChunk.isNullOrEmpty()) {
            log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
            val timelineEvents = previousEventChunk.mapIndexed { index, event ->
                when (index) {
                    previousEventChunk.lastIndex -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = previousEvent?.eventId,
                            nextEventId = if (index == 0) startEvent.eventId
                            else previousEventChunk.getOrNull(index - 1)?.id,
                            gap = if (previousHasGap) previousToken?.let { GapBefore(it) } else null
                        )
                    }
                    0 -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = previousEventChunk.getOrNull(1)?.id,
                            nextEventId = startEvent.eventId,
                            gap = null
                        )
                    }
                    else -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = previousEventChunk.getOrNull(index + 1)?.id,
                            nextEventId = previousEventChunk.getOrNull(index - 1)?.id,
                            gap = null
                        )
                    }
                }
            }
            store.roomTimeline.addAll(timelineEvents, withTransaction = false)
        }

        if (!nextEventChunk.isNullOrEmpty()) {
            log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
            val timelineEvents = nextEventChunk.mapIndexed { index, event ->
                when (index) {
                    nextEventChunk.lastIndex -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = if (index == 0) startEvent.eventId
                            else nextEventChunk.getOrNull(index - 1)?.id,
                            nextEventId = nextEvent?.eventId,
                            gap = if (nextHasGap) nextToken?.let { GapAfter(it) } else null,
                        )
                    }
                    0 -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = startEvent.eventId,
                            nextEventId = nextEventChunk.getOrNull(1)?.id,
                            gap = null
                        )
                    }
                    else -> {
                        TimelineEvent(
                            event = event,
                            roomId = roomId,
                            eventId = event.id,
                            previousEventId = nextEventChunk.getOrNull(index - 1)?.id,
                            nextEventId = nextEventChunk.getOrNull(index + 1)?.id,
                            gap = null
                        )
                    }
                }
            }
            store.roomTimeline.addAll(modifyTimelineEventsBeforeSave(timelineEvents), withTransaction = false)
        }
    }

    private fun TimelineEvent.canBeDecrypted(): Boolean =
        this.event is MessageEvent
                && this.event.isEncrypted
                && this.content == null

    /**
     * @param scope The [CoroutineScope] is used to fetch and/or decrypt the [TimelineEvent] and to determine,
     * how long the [TimelineEvent] should be hold in cache.
     */
    override suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        fetchNeighborLimit: Long,
    ): StateFlow<TimelineEvent?> {
        return store.roomTimeline.get(eventId, roomId, coroutineScope).also { timelineEventFlow ->
            coroutineScope.launch {
                val timelineEvent = timelineEventFlow.value ?: withTimeoutOrNull(fetchTimeout) {
                    log.info { "cannot find TimelineEvent $eventId in store. we try to fetch it." }
                    fetchMissingEvents(eventId, roomId, fetchNeighborLimit)
                    store.roomTimeline.get(eventId, roomId)
                }
                val content = timelineEvent?.event?.content
                if (timelineEvent?.canBeDecrypted() == true && content is MegolmEncryptedEventContent) {
                    withTimeoutOrNull(decryptionTimeout) {
                        val session =
                            store.olm.getInboundMegolmSession(content.senderKey, content.sessionId, roomId, this)
                        val firstKnownIndex = session.value?.firstKnownIndex
                        if (session.value == null) {
                            keyBackup.loadMegolmSession(roomId, content.sessionId, content.senderKey)
                            log.debug { "start to wait for inbound megolm session to decrypt $eventId in $roomId" }
                            store.olm.waitForInboundMegolmSession(roomId, content.sessionId, content.senderKey, this)
                        }
                        log.trace { "try to decrypt event $eventId in $roomId" }
                        @Suppress("UNCHECKED_CAST")
                        val encryptedEvent = timelineEvent.event as MessageEvent<MegolmEncryptedEventContent>

                        val decryptEventAttempt = encryptedEvent.decryptCatching()
                        val exception = decryptEventAttempt.exceptionOrNull()
                        val decryptedEvent =
                            if (exception is OlmLibraryException && exception.message?.contains("UNKNOWN_MESSAGE_INDEX") == true
                                || exception is DecryptionException.SessionException && exception.cause.message?.contains(
                                    "UNKNOWN_MESSAGE_INDEX"
                                ) == true
                            ) {
                                keyBackup.loadMegolmSession(roomId, content.sessionId, content.senderKey)
                                log.debug { "unknwon message index, so we start to wait for inbound megolm session to decrypt $eventId in $roomId again" }
                                store.olm.waitForInboundMegolmSession(
                                    roomId,
                                    content.sessionId,
                                    content.senderKey,
                                    this,
                                    firstKnownIndexLessThen = firstKnownIndex
                                )
                                encryptedEvent.decryptCatching()
                            } else decryptEventAttempt
                        store.roomTimeline.update(eventId, roomId, persistIntoRepository = false) { oldEvent ->
                            // we check here again, because an event could be redacted at the same time
                            if (oldEvent?.canBeDecrypted() == true) timelineEvent.copy(content = decryptedEvent.map { it.content })
                            else oldEvent
                        }
                        log.trace { "decrypted TimelineEvent $eventId in $roomId" }
                    }
                }
            }
        }
    }

    private suspend fun MessageEvent<MegolmEncryptedEventContent>.decryptCatching(): Result<DecryptedMegolmEvent<*>> {
        return try {
            Result.success(olmEvent.decryptMegolm(this))
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            else Result.failure(ex)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): Flow<StateFlow<TimelineEvent?>?> {
        return store.room.get(roomId).transformLatest { room ->
            coroutineScope {
                if (room?.lastEventId != null) emit(getTimelineEvent(room.lastEventId, roomId, this, decryptionTimeout))
                else emit(null)
                delay(INFINITE) // ensure, that the TimelineEvent does not get removed from cache
            }
        }.distinctUntilChanged()
    }

    override suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration
    ): StateFlow<TimelineEvent?>? {
        return event.previousEventId?.let { getTimelineEvent(it, event.roomId, coroutineScope, decryptionTimeout) }
    }

    override suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration
    ): StateFlow<TimelineEvent?>? {
        return event.nextEventId?.let { getTimelineEvent(it, event.roomId, coroutineScope, decryptionTimeout) }
    }

    override suspend fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: Direction,
        decryptionTimeout: Duration
    ): Flow<StateFlow<TimelineEvent?>> =
        channelFlow {
            fun TimelineEvent.Gap?.hasGap() =
                this != null && (this.hasGapBoth
                        || direction == FORWARDS && this.hasGapAfter
                        || direction == BACKWARDS && this.hasGapBefore)

            var currentTimelineEventFlow: StateFlow<TimelineEvent?> =
                getTimelineEvent(startFrom, roomId, this, decryptionTimeout)
            send(currentTimelineEventFlow)
            do {
                currentTimelineEventFlow = currentTimelineEventFlow
                    .filterNotNull()
                    .onEach { currentTimelineEvent ->
                        val gap = currentTimelineEvent.gap
                        if (gap.hasGap()) {
                            log.debug { "found $gap at ${currentTimelineEvent.eventId}" }
                            fetchMissingEvents(currentTimelineEvent.eventId, currentTimelineEvent.roomId)
                        }
                    }
                    .filter { it.gap.hasGap().not() }
                    .map { currentTimelineEvent ->
                        when (direction) {
                            BACKWARDS -> getPreviousTimelineEvent(currentTimelineEvent, this, decryptionTimeout)
                            FORWARDS -> getNextTimelineEvent(currentTimelineEvent, this, decryptionTimeout)
                        }
                    }
                    .filterNotNull()
                    .first()
                send(currentTimelineEventFlow)
            } while (direction != BACKWARDS || currentTimelineEventFlow.value?.isFirst == false)
            close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): Flow<Flow<StateFlow<TimelineEvent?>>?> =
        store.room.get(roomId)
            .mapLatest { it?.lastEventId }
            .distinctUntilChanged()
            .mapLatest {
                if (it != null) getTimelineEvents(it, roomId, BACKWARDS, decryptionTimeout)
                else null
            }

    @OptIn(FlowPreview::class)
    override fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int,
    ): Flow<TimelineEvent> =
        callbackFlow {
            val subscriber: AfterSyncResponseSubscriber = { send(it) }
            api.sync.subscribeAfterSyncResponse(subscriber)
            awaitClose { api.sync.unsubscribeAfterSyncResponse(subscriber) }
        }.flatMapConcat { syncResponse ->
            coroutineScope {
                val timelineEvents =
                    syncResponse.room?.join?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty() +
                            syncResponse.room?.leave?.values?.flatMap { it.timeline?.events.orEmpty() }.orEmpty()
                timelineEvents.map {
                    async {
                        getTimelineEvent(it.id, it.roomId, this, decryptionTimeout)
                    }
                }.asFlow()
                    .map {
                        it.await().value
                    }.filterNotNull()
            }
        }

    override suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit) {
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

    override suspend fun abortSendMessage(transactionId: String) {
        store.roomOutboxMessage.update(transactionId) { null }
    }

    override suspend fun retrySendMessage(transactionId: String) {
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
                log.debug { "remove outbox message with transaction ${it.transactionId} (sent ${it.sentAt}), because it should be already synced" }
                store.roomOutboxMessage.update(it.transactionId) { null }
            }
        }
    }

    internal suspend fun processOutboxMessages(outboxMessages: Flow<List<RoomOutboxMessage<*>>>) {
        currentSyncState.retryInfiniteWhenSyncIs(
            RUNNING,
            onError = { log.warn(it) { "failed sending outbox messages" } },
            onCancel = { log.info { "stop sending outbox messages, because job was cancelled" } },
        ) {
            log.debug { "start sending outbox messages" }
            outboxMessages.scan(listOf<RoomOutboxMessage<*>>()) { old, new ->
                // the flow from store.roomOutboxMessage.getAll() needs some time to get updated, when one entry is updated
                // therefore we compare the lists and if they did not change, we do nothing (distinctUntilChanged)
                if (old.map { it.transactionId }.toSet() != new.map { it.transactionId }.toSet()) new
                else old
            }.distinctUntilChanged().collect { outboxMessagesList ->
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
                                possiblyEncryptEvent(uploadedContent, roomId, store, olmEvent, user)
                            }
                        log.trace { "send to $roomId : $content" }
                        val eventId =
                            api.rooms.sendMessageEvent(roomId, content, outboxMessage.transactionId).getOrThrow()
                        if (config.setOwnMessagesAsFullyRead) {
                            api.rooms.setReadMarkers(roomId, eventId).getOrThrow()
                        }
                        store.roomOutboxMessage.update(outboxMessage.transactionId) { it?.copy(sentAt = Clock.System.now()) }
                        log.debug { "sent message with transactionId '${outboxMessage.transactionId}' and content $content" }
                    }
            }
        }
    }

    override fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> = store.room.getAll()

    override suspend fun getById(roomId: RoomId): StateFlow<Room?> {
        return store.room.get(roomId)
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
        scope: CoroutineScope
    ): Flow<C?> {
        return store.roomAccountData.get(roomId, eventContentClass, key, scope)
            .map { it?.content }
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
    ): C? {
        return store.roomAccountData.get(roomId, eventContentClass, key)?.content
    }

    override fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> = store.roomOutboxMessage.getAll()

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): Flow<Event<C>?> {
        return store.roomState.getByStateKey(roomId, stateKey, eventContentClass, scope)
    }

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
    ): Event<C>? {
        return store.roomState.getByStateKey(roomId, stateKey, eventContentClass)
    }
}