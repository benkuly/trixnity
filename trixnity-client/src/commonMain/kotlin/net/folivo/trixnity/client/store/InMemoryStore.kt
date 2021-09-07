package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getRoomIdAndStateKey
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
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

    class InMemoryServerStore(override val hostname: String, override val port: Int, override val secure: Boolean) :
        Store.ServerStore

    class InMemoryAccountStore : Store.AccountStore {
        override val userId: MutableStateFlow<MatrixId.UserId?> = MutableStateFlow(null)
        override val deviceId: MutableStateFlow<String?> = MutableStateFlow(null)
        override val accessToken: MutableStateFlow<String?> = MutableStateFlow(null)
        override val syncBatchToken: MutableStateFlow<String?> = MutableStateFlow(null)
        override val filterId: MutableStateFlow<String?> = MutableStateFlow(null)
    }

    class InMemoryRoomsStore : Store.RoomsStore {

        private val scope = CoroutineScope(Dispatchers.Default)

        override val state = InMemoryRoomStateStore(scope)
        override val timeline = InMemoryRoomTimelineStore()

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
            private val states: MutableMap<RoomId, MutableMap<KClass<out StateEventContent>, MutableStateFlow<Map<String, Event<StateEventContent>>>>> =
                mutableMapOf()

            override suspend fun update(event: Event<StateEventContent>) {
                val (roomId, stateKey) = event.getRoomIdAndStateKey()
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

            override suspend fun updateAll(events: List<Event<StateEventContent>>) {
                events.forEach { update(it) }
            }

            override suspend fun <C : StateEventContent> byId(
                roomId: RoomId,
                eventContentClass: KClass<C>
            ): StateFlow<Map<String, Event<C>>> {
                val roomState = states[roomId]
                val state = roomState?.get(eventContentClass)
                return if (state == null) {
                    MutableStateFlow<Map<String, Event.StateEvent<C>>>(mapOf()).also {
                        @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                        states.getOrPut(roomId) { mutableMapOf() }[eventContentClass] =
                            it as MutableStateFlow<Map<String, Event<StateEventContent>>>
                    }.asStateFlow()
                } else {
                    @Suppress("UNCHECKED_CAST") // TODO unchecked cast
                    state.asStateFlow() as StateFlow<Map<String, Event<C>>>
                }
            }

            override suspend fun <C : StateEventContent> byId(
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
                            it as MutableStateFlow<Map<String, Event<StateEventContent>>>
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
            ): StateFlow<TimelineEvent?> {
                val event = timelineEvents.getOrPut(eventId + roomId) { MutableStateFlow(null) }
                event.update { updater(it) }
                return event
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
    }

    class InMemoryDeviceKeysStore : Store.DeviceKeysStores {
        private val deviceKeys: MutableMap<MatrixId.UserId, MutableStateFlow<Map<String, DeviceKeys>?>> = mutableMapOf()
        override suspend fun byUserId(userId: MatrixId.UserId): MutableStateFlow<Map<String, DeviceKeys>?> {
            return deviceKeys.getOrPut(userId) { MutableStateFlow(null) }
        }

        override val outdatedKeys: MutableStateFlow<Set<MatrixId.UserId>> = MutableStateFlow(setOf())
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
}