package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import net.folivo.trixnity.client.room.RoomService
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
import kotlin.reflect.KClass
import kotlin.time.Duration

class RoomServiceMock : RoomService {
    override suspend fun fillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long) {
        throw NotImplementedError()
    }

    lateinit var returnGetTimelineEvent: Flow<TimelineEvent?>
    override fun getTimelineEvent(
        eventId: EventId,
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
    ): Flow<TimelineEvent?> {
        return returnGetTimelineEvent
    }

    override fun getPreviousTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long
    ): Flow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    override fun getNextTimelineEvent(
        event: TimelineEvent,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long
    ): Flow<TimelineEvent?>? {
        throw NotImplementedError()
    }

    override fun getLastTimelineEvent(
        roomId: RoomId,
        decryptionTimeout: Duration
    ): StateFlow<StateFlow<TimelineEvent>?> {
        throw NotImplementedError()
    }

    var returnGetTimelineEvents: Flow<Flow<TimelineEvent>> = flowOf()

    override fun getTimelineEvents(
        startFrom: EventId,
        roomId: RoomId,
        direction: GetEvents.Direction,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        minSize: Long?,
        maxSize: Long?
    ): Flow<Flow<TimelineEvent>> = returnGetTimelineEvents

    override fun getLastTimelineEvents(
        roomId: RoomId,
        decryptionTimeout: Duration,
        fetchTimeout: Duration,
        limitPerFetch: Long,
        minSize: Long?,
        maxSize: Long?
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

    override fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
    ): Flow<Map<RelationType, Set<TimelineEventRelation>?>?> {
        throw NotImplementedError()
    }

    override fun getTimelineEventRelations(
        eventId: EventId,
        roomId: RoomId,
        relationType: RelationType,
    ): Flow<Set<TimelineEventRelation>?> {
        throw NotImplementedError()
    }

    override suspend fun sendMessage(
        roomId: RoomId,
        keepMediaInCache: Boolean,
        builder: suspend MessageBuilder.() -> Unit
    ) {
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

    override fun getById(roomId: RoomId): StateFlow<Room?> {
        throw NotImplementedError()
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
        stateKey: String,
        eventContentClass: KClass<C>,
    ): StateFlow<Event<C>?> {
        throw NotImplementedError()
    }

    override fun <C : StateEventContent> getAllState(
        roomId: RoomId,
        eventContentClass: KClass<C>,
    ): Flow<Map<String, Event<C>?>?> {
        throw NotImplementedError()
    }

    override fun canBeRedacted(timelineEvent: TimelineEvent): Flow<Boolean> {
        throw NotImplementedError()
    }
}