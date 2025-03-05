package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.flatten
import net.folivo.trixnity.client.room.*
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import kotlin.reflect.KClass
import kotlin.time.Duration

class RoomServiceMock : RoomService {
    override val usersTyping: StateFlow<Map<RoomId, TypingEventContent>> = MutableStateFlow(mapOf())
    override suspend fun fillTimelineGaps(roomId: RoomId, startEventId: EventId, limit: Long) {
        throw NotImplementedError()
    }

    lateinit var returnGetTimelineEvent: Flow<TimelineEvent>
    override fun getTimelineEvent(
        roomId: RoomId,
        eventId: EventId,
        config: GetTimelineEventConfig.() -> Unit
    ): Flow<TimelineEvent?> {
        return returnGetTimelineEvent
    }

    override fun getPreviousTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit
    ): Flow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    override fun getNextTimelineEvent(
        event: TimelineEvent,
        config: GetTimelineEventConfig.() -> Unit
    ): Flow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    override fun getLastTimelineEvent(
        roomId: RoomId,
        config: GetTimelineEventConfig.() -> Unit
    ): Flow<Flow<TimelineEvent>?> {
        throw NotImplementedError()
    }

    var returnGetTimelineEvents: Flow<Flow<TimelineEvent>> = flowOf()

    override fun getTimelineEvents(
        roomId: RoomId,
        startFrom: EventId,
        direction: GetEvents.Direction,
        config: GetTimelineEventsConfig.() -> Unit
    ): Flow<Flow<TimelineEvent>> = returnGetTimelineEvents

    override fun getLastTimelineEvents(
        roomId: RoomId,
        config: GetTimelineEventsConfig.() -> Unit
    ): Flow<Flow<Flow<TimelineEvent>>?> {
        throw NotImplementedError()
    }

    var returnGetTimelineEventsFromNowOn: Flow<TimelineEvent> = flowOf()
    override fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int
    ): Flow<TimelineEvent> {
        return returnGetTimelineEventsFromNowOn
    }

    override fun <T> getTimeline(
        roomId: RoomId,
        onStateChange: suspend (TimelineStateChange<T>) -> Unit,
        transformer: suspend (Flow<TimelineEvent>) -> T
    ): Timeline<T> {
        throw NotImplementedError()
    }

    var returnGetTimelineEventsOnce: Flow<TimelineEvent> = flowOf()
    override fun getTimelineEventsOnce(response: Sync.Response, decryptionTimeout: Duration): Flow<TimelineEvent> {
        return returnGetTimelineEventsOnce
    }

    override fun getTimelineEventRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType
    ): Flow<Map<EventId, Flow<TimelineEventRelation?>>?> {
        throw NotImplementedError()
    }

    var sentMessages = MutableStateFlow(listOf<Pair<RoomId, MessageEventContent>>())
    override suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean,
        builder: suspend MessageBuilder.() -> Unit
    ): String {
        sentMessages.update {
            it + (roomId to
                    requireNotNull(
                        MessageBuilder(roomId, RoomServiceMock(), MediaServiceMock(), UserId("own", "server"))
                            .build(builder)
                    ))
        }
        return sentMessages.value.size.toString()
    }

    override suspend fun cancelSendMessage(roomId: RoomId, transactionId: String) {
        throw NotImplementedError()
    }

    override suspend fun retrySendMessage(roomId: RoomId, transactionId: String) {
        throw NotImplementedError()
    }

    override fun getAll(): StateFlow<Map<RoomId, StateFlow<Room?>>> {
        throw NotImplementedError()
    }

    val rooms = MutableStateFlow(mapOf<RoomId, StateFlow<Room?>>())
    override fun getById(roomId: RoomId): StateFlow<Room?> {
        return checkNotNull(rooms.value[roomId])
    }

    val forgetRooms = MutableStateFlow(listOf<RoomId>())
    override suspend fun forgetRoom(roomId: RoomId) {
        forgetRooms.update { it + roomId }
    }

    override fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
    ): StateFlow<C?> {
        throw NotImplementedError()
    }

    val outbox = MutableStateFlow(listOf<Flow<RoomOutboxMessage<*>?>>())
    override fun getOutbox(): Flow<List<Flow<RoomOutboxMessage<*>?>>> = outbox
    override fun getOutbox(roomId: RoomId): Flow<List<Flow<RoomOutboxMessage<*>?>>> =
        outbox.map { outbox ->
            outbox.map { it.filterNotNull().first() to it }
                .filter { it.first.roomId == roomId }
                .map { it.second }
        }.distinctUntilChanged()

    override fun getOutbox(roomId: RoomId, transactionId: String): Flow<RoomOutboxMessage<*>?> =
        outbox.flatten().map { it.find { it.roomId == roomId && it.transactionId == transactionId } }

    data class GetStateKey(
        val roomId: RoomId,
        val eventContentClass: KClass<out StateEventContent>,
        val stateKey: String = ""
    )

    val state = MutableStateFlow<Map<GetStateKey, StateBaseEvent<*>?>>(mapOf())
    override fun <C : StateEventContent> getState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String
    ): Flow<StateBaseEvent<C>?> {
        @Suppress("UNCHECKED_CAST")
        return flowOf(state.value.entries.find { it.key.eventContentClass == eventContentClass }?.value as StateBaseEvent<C>?)
    }

    override fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<StateBaseEvent<C>?>>> {
        throw NotImplementedError()
    }
}
