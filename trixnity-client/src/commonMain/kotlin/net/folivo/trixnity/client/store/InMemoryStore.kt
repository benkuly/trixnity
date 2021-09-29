package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import kotlin.reflect.KClass

class InMemoryStore(
    private val hostname: String = "",
    private val port: Int = 443,
    private val secure: Boolean = true
) : Store {
    override suspend fun clear() {
        server = InMemoryServerStore(hostname, port, secure)
        account = InMemoryAccountStore()
        rooms = InMemoryRoomsStore()
        deviceKeys = InMemoryDeviceKeysStore()
        olm = InMemoryOlmStore()
        media = InMemoryMediaStore()
    }

    override var server = InMemoryServerStore(hostname, port, secure)
        private set
    override var account = InMemoryAccountStore()
        private set
    override var rooms = InMemoryRoomsStore()
        private set
    override var deviceKeys = InMemoryDeviceKeysStore()
        private set
    override var olm = InMemoryOlmStore()
        private set
    override var media = InMemoryMediaStore()
        private set

    class InMemoryServerStore(override val hostname: String, override val port: Int, override val secure: Boolean) :
        Store.ServerStore

    class InMemoryAccountStore : Store.AccountStore {
        override val userId: MutableStateFlow<UserId?> = MutableStateFlow(null)
        override val deviceId: MutableStateFlow<String?> = MutableStateFlow(null)
        override val accessToken: MutableStateFlow<String?> = MutableStateFlow(null)
        override val syncBatchToken: MutableStateFlow<String?> = MutableStateFlow(null)
        override val filterId: MutableStateFlow<String?> = MutableStateFlow(null)
    }

    class InMemoryRoomsStore : Store.RoomsStore {

        private val scope = CoroutineScope(Dispatchers.Default)

        override val state = InMemoryRoomStateStore(scope)
        override val timeline = InMemoryRoomTimelineStore()
        override val users = InMemoryRoomUserStore(scope)
        override val outboxMessages = InMemoryRoomOutboxMessagesStore(scope)

        private val rooms: MutableStateFlow<Map<RoomId, Room?>> = MutableStateFlow(mapOf())

        override suspend fun all(): StateFlow<Set<Room>> {
            return rooms.map { roomSet -> roomSet.mapNotNull { it.value }.toSet() }.stateIn(scope)
        }

        override suspend fun byId(roomId: RoomId): StateFlow<Room?> {
            return rooms.map { it[roomId] }.stateIn(scope)
        }

        override suspend fun update(roomId: RoomId, updater: suspend (oldRoom: Room?) -> Room?): StateFlow<Room?> {
            rooms.update { oldRooms ->
                oldRooms + mapOf(roomId to updater(oldRooms[roomId]))
            }
            return byId(roomId)
        }

        class InMemoryRoomStateStore(private val scope: CoroutineScope) : Store.RoomsStore.RoomStateStore {
            private val states: MutableMap<RoomId, MutableMap<KClass<out StateEventContent>, MutableStateFlow<Map<String, Event<out StateEventContent>>>>> =
                mutableMapOf()

            override suspend fun update(event: Event<out StateEventContent>) {
                val roomId = event.getRoomId()
                val stateKey = event.getStateKey()
                if (roomId != null && stateKey != null) {
                    val eventContentClass = event.content::class
                    val roomState = states[roomId]
                    val state = roomState?.get(eventContentClass)
                    if (state == null) {
                        states.getOrPut(roomId) { mutableMapOf() }[eventContentClass] =
                            MutableStateFlow(mapOf(stateKey to event))
                    } else {
                        state.update { it + mapOf(stateKey to event) }
                    }
                }
            }

            override suspend fun updateAll(events: List<Event.StateEvent<out StateEventContent>>) {
                events.forEach { update(it) }
            }

            override suspend fun <C : StateEventContent> allById(
                roomId: RoomId,
                eventContentClass: KClass<C>
            ): StateFlow<Map<String, Event<C>>> {
                val roomState = states[roomId]
                val state = roomState?.get(eventContentClass)
                return if (state == null) {
                    MutableStateFlow<Map<String, Event.StateEvent<C>>>(mapOf()).also {
                        @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                        states.getOrPut(roomId) { mutableMapOf() }[eventContentClass] =
                            it as MutableStateFlow<Map<String, Event<out StateEventContent>>>
                    }.asStateFlow()
                } else {
                    @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                    state.asStateFlow() as StateFlow<Map<String, Event<C>>>
                }
            }

            override suspend fun <C : StateEventContent> allById(
                roomId: RoomId,
                stateKey: String,
                eventContentClass: KClass<C>
            ): StateFlow<Event<C>?> {
                val roomState = states[roomId]
                val state = roomState?.get(eventContentClass)
                return if (state == null) {
                    MutableStateFlow<Map<String, Event.StateEvent<C>>>(mapOf()).also {
                        @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                        states.getOrPut(roomId) { mutableMapOf() }[eventContentClass] =
                            it as MutableStateFlow<Map<String, Event<out StateEventContent>>>
                    }.map { it[stateKey] }.stateIn(scope)
                } else {
                    @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                    state.map { it[stateKey] }.stateIn(scope) as StateFlow<Event.StateEvent<C>>
                }
            }
        }

        class InMemoryRoomTimelineStore : Store.RoomsStore.RoomTimelineStore {
            private val timelineEvents = mutableMapOf<String, MutableStateFlow<TimelineEvent?>>()

            override suspend fun update(
                eventId: MatrixId.EventId,
                roomId: RoomId,
                updater: suspend (oldRoom: TimelineEvent?) -> TimelineEvent?
            ): TimelineEvent? {
                val event = timelineEvents.getOrPut(eventId + roomId) { MutableStateFlow(null) }
                event.update { updater(it) }
                return event.value
            }

            override suspend fun updateAll(events: List<TimelineEvent>) {
                events.forEach { rawEvent ->
                    update(rawEvent.eventId, rawEvent.roomId) { rawEvent }
                }
            }

            override suspend fun byId(eventId: MatrixId.EventId, roomId: RoomId): StateFlow<TimelineEvent?> {
                return timelineEvents.getOrPut(eventId + roomId) { MutableStateFlow(null) }
            }
        }

        class InMemoryRoomUserStore(private val scope: CoroutineScope) : Store.RoomsStore.RoomUserStore {
            private val roomUsers = MutableStateFlow<Map<Pair<UserId, RoomId>, RoomUser?>>(mapOf())

            override suspend fun all(): StateFlow<Set<RoomUser>> {
                return roomUsers.map { roomUsersMap -> roomUsersMap.mapNotNull { it.value }.toSet() }.stateIn(scope)
            }

            override suspend fun byId(userId: UserId, roomId: RoomId): StateFlow<RoomUser?> {
                return roomUsers.map { it[userId to roomId] }.stateIn(scope)
            }

            override suspend fun update(
                userId: UserId,
                roomId: RoomId,
                updater: suspend (oldRoomUser: RoomUser?) -> RoomUser?
            ): StateFlow<RoomUser?> {
                roomUsers.update { oldRoomUsers ->
                    oldRoomUsers + mapOf((userId to roomId) to updater(oldRoomUsers[(userId to roomId)]))
                }
                return byId(userId, roomId)
            }

            override suspend fun byOriginalNameAndMembership(
                originalName: String,
                membership: Set<MemberEventContent.Membership>,
                roomId: RoomId
            ): Set<UserId> {
                return roomUsers.value.asSequence()
                    .filter { it.key.second == roomId }
                    .mapNotNull { it.value }
                    .filter { it.originalName == originalName }
                    .filter { membership.contains(it.membership) }
                    .map { it.userId }
                    .toSet()
            }
        }

        class InMemoryRoomOutboxMessagesStore(private val scope: CoroutineScope) :
            Store.RoomsStore.RoomOutboxMessagesStore {
            private val outboxMessages = MutableStateFlow(listOf<RoomOutboxMessage>())

            override suspend fun add(message: RoomOutboxMessage) {
                outboxMessages.update { it + message }
            }

            override suspend fun deleteByTransactionId(transactionId: String) {
                outboxMessages.update { list -> list.filter { it.transactionId != transactionId } }
            }

            override suspend fun markAsSent(transactionId: String) {
                outboxMessages.update { list ->
                    list.map {
                        if (it.transactionId == transactionId) it.copy(wasSent = true) else it
                    }
                }
            }

            override suspend fun all(): StateFlow<List<RoomOutboxMessage>> {
                return outboxMessages.asStateFlow()
            }

            override suspend fun allByRoomId(roomId: RoomId): StateFlow<List<RoomOutboxMessage>> {
                return outboxMessages.map { list -> list.filter { it.roomId == roomId } }.stateIn(scope)
            }

        }
    }

    class InMemoryDeviceKeysStore : Store.DeviceKeysStores {
        private val deviceKeys: MutableMap<UserId, MutableStateFlow<Map<String, DeviceKeys>?>> = mutableMapOf()
        override suspend fun byUserId(userId: UserId): MutableStateFlow<Map<String, DeviceKeys>?> {
            return deviceKeys.getOrPut(userId) { MutableStateFlow(null) }
        }

        override val outdatedKeys: MutableStateFlow<Set<UserId>> = MutableStateFlow(setOf())
    }

    class InMemoryOlmStore : Store.OlmStore {
        override val pickleKey: String = ""
        override val account: MutableStateFlow<String?> = MutableStateFlow(null)

        private val olmSessions: MutableMap<Key.Curve25519Key, MutableStateFlow<Set<StoredOlmSession>>> = mutableMapOf()
        override suspend fun olmSessions(senderKey: Key.Curve25519Key): MutableStateFlow<Set<StoredOlmSession>> {
            return olmSessions.getOrPut(senderKey) { MutableStateFlow(setOf()) }
        }

        private val inboundMegolmSession: MutableMap<String, MutableStateFlow<StoredOlmInboundMegolmSession?>> =
            mutableMapOf()

        override suspend fun inboundMegolmSession(
            roomId: RoomId,
            sessionId: String,
            senderKey: Key.Curve25519Key
        ): MutableStateFlow<StoredOlmInboundMegolmSession?> {
            return inboundMegolmSession.getOrPut(roomId.full + sessionId + senderKey.value) { MutableStateFlow(null) }
        }

        private val inboundMegolmMessageIndex: MutableMap<String, MutableStateFlow<StoredMegolmMessageIndex?>> =
            mutableMapOf()

        override suspend fun inboundMegolmMessageIndex(
            roomId: RoomId,
            sessionId: String,
            senderKey: Key.Curve25519Key,
            messageIndex: Long
        ): MutableStateFlow<StoredMegolmMessageIndex?> {
            return inboundMegolmMessageIndex.getOrPut(roomId.full + sessionId + senderKey + messageIndex) {
                MutableStateFlow(null)
            }
        }

        private val outboundMegolmSession: MutableMap<RoomId, MutableStateFlow<StoredOutboundMegolmSession?>> =
            mutableMapOf()

        override suspend fun outboundMegolmSession(roomId: RoomId): MutableStateFlow<StoredOutboundMegolmSession?> {
            return outboundMegolmSession.getOrPut(roomId) { MutableStateFlow(null) }
        }
    }

    class InMemoryMediaStore : Store.MediaStore {

        private val media = mutableMapOf<String, ByteArray>()
        private val uploadMediaCache = mutableMapOf<String, UploadMediaCache?>()

        override suspend fun addContent(uri: String, content: ByteArray) {
            this.media[uri] = content
        }

        override suspend fun byUri(uri: String): ByteArray? {
            return this.media[uri]
        }

        override suspend fun changeUri(oldUri: String, newUri: String) {
            this.media[oldUri]?.also { this.media[newUri] = it }
            this.media.remove(oldUri)
        }

        override suspend fun getUploadMediaCache(cacheUri: String): UploadMediaCache? {
            return uploadMediaCache[cacheUri]
        }

        override suspend fun updateUploadMediaCache(
            cacheUri: String,
            updater: suspend (oldUploadMediaCache: UploadMediaCache?) -> UploadMediaCache?
        ) {
            val oldCache = uploadMediaCache[cacheUri]
            if (oldCache == null) {
                updater(null)?.also { uploadMediaCache[cacheUri] = it }
            } else {
                uploadMediaCache[cacheUri] = updater(oldCache)
            }
        }
    }
}