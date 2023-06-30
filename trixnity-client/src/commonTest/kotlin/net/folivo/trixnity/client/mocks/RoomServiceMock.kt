package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.room.GetTimelineEventConfig
import net.folivo.trixnity.client.room.GetTimelineEventsConfig
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.Timeline
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RelationType
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
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

    override fun <T> getTimeline(roomId: RoomId, transformer: suspend (Flow<TimelineEvent>) -> T): Timeline<T> {
        throw NotImplementedError()
    }

    override fun getTimelineEventRelations(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<Map<RelationType, Flow<Set<TimelineEventRelation>?>>?> {
        throw NotImplementedError()
    }

    override fun getTimelineEventRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
    ): Flow<Set<TimelineEventRelation>?> {
        throw NotImplementedError()
    }

    override suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean,
        builder: suspend MessageBuilder.() -> Unit
    ): String {
        throw NotImplementedError()
    }

    override suspend fun abortSendMessage(transactionId: String) {
        throw NotImplementedError()
    }

    override suspend fun retrySendMessage(transactionId: String) {
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

    override fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> {
        throw NotImplementedError()
    }

    override fun <C : StateEventContent> getState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        stateKey: String
    ): Flow<Event<C>?> {
        throw NotImplementedError()
    }

    override fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Flow<Event<C>?>>?> {
        throw NotImplementedError()
    }
}