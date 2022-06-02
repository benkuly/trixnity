package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.room.message.MessageBuilder
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass
import kotlin.time.Duration

class RoomServiceMock : IRoomService {
    override suspend fun fetchMissingEvents(startEventId: EventId, roomId: RoomId, limit: Long): Result<Unit> {
        throw NotImplementedError()
    }

    lateinit var returnGetTimelineEvent: StateFlow<TimelineEvent?>
    override suspend fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        fetchNeighborLimit: Long,
    ): StateFlow<TimelineEvent?> {
        return returnGetTimelineEvent
    }

    override suspend fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): StateFlow<StateFlow<TimelineEvent?>?> {
        throw NotImplementedError()
    }

    override suspend fun getPreviousTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration
    ): StateFlow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    override suspend fun getNextTimelineEvent(
        event: TimelineEvent,
        coroutineScope: CoroutineScope,
        decryptionTimeout: Duration
    ): StateFlow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    var returnGetTimelineEvents: Flow<StateFlow<TimelineEvent?>> = flowOf()
    override suspend fun getTimelineEvents(
        startFrom: StateFlow<TimelineEvent?>,
        direction: GetEvents.Direction,
        decryptionTimeout: Duration
    ): Flow<StateFlow<TimelineEvent?>> {
        return returnGetTimelineEvents
    }

    override suspend fun getLastTimelineEvents(
        roomId: RoomId,
    ): Flow<Flow<StateFlow<TimelineEvent?>>?> {
        throw NotImplementedError()
    }

    var returnGetTimelineEventsFromNowOn: Flow<TimelineEvent> = flowOf()
    override fun getTimelineEventsFromNowOn(
        decryptionTimeout: Duration,
        syncResponseBufferSize: Int
    ): Flow<TimelineEvent> {
        return returnGetTimelineEventsFromNowOn
    }

    override suspend fun sendMessage(roomId: RoomId, builder: suspend MessageBuilder.() -> Unit) {
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

    override suspend fun getById(roomId: RoomId): StateFlow<Room?> {
        throw NotImplementedError()
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String,
        scope: CoroutineScope
    ): StateFlow<C?> {
        throw NotImplementedError()
    }

    override suspend fun <C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        eventContentClass: KClass<C>,
        key: String
    ): C? {
        throw NotImplementedError()
    }

    override fun getOutbox(): StateFlow<List<RoomOutboxMessage<*>>> {
        throw NotImplementedError()
    }

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>,
        scope: CoroutineScope
    ): StateFlow<Event<C>?> {
        throw NotImplementedError()
    }

    override suspend fun <C : StateEventContent> getState(
        roomId: RoomId,
        stateKey: String,
        eventContentClass: KClass<C>
    ): Event<C>? {
        throw NotImplementedError()
    }
}