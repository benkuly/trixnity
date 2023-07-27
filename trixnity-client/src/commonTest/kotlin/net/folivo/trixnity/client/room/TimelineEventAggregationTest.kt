package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventDecryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.ServerAggregation
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class TimelineEventAggregationTest : ShouldSpec({
    timeout = 5_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var roomEventDecryptionServiceMock: RoomEventDecryptionServiceMock
    val json = createMatrixEventJson()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)

        mediaServiceMock = MediaServiceMock()
        roomEventDecryptionServiceMock = RoomEventDecryptionServiceMock()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomServiceImpl(
            api,
            roomStore, roomUserStore, roomStateStore, roomAccountDataStore, roomTimelineStore, roomOutboxMessageStore,
            listOf(roomEventDecryptionServiceMock),
            mediaServiceMock,
            simpleUserInfo,
            TimelineEventHandlerMock(),
            TypingEventHandler(api),
            CurrentSyncState(currentSyncState),
            scope
        )
    }

    afterTest {
        scope.cancel()
    }

    fun timelineEvent(
        id: String,
        originTimestamp: Long = 1234,
        sender: UserId = UserId("sender", "server"),
        replacedBy: ServerAggregation.Replace? = null
    ): TimelineEvent =
        TimelineEvent(
            event = Event.MessageEvent(
                RoomMessageEventContent.TextMessageEventContent(id),
                EventId(id),
                sender,
                room,
                originTimestamp,
                UnsignedRoomEventData.UnsignedMessageEventData(relations = buildMap {
                    replacedBy?.also { put(RelationType.Replace, it) }
                })
            ),
            gap = null,
            nextEventId = null,
            previousEventId = null,
        )

    context(RoomService::getTimelineEventReplaceAggregation.name) {
        beforeTest {
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent("1", 1),
                    timelineEvent("2", 2),
                    timelineEvent("3", 3),
                    timelineEvent("4", 4, UserId("otherSender", "server"))
                )
            )
        }
        should("use latest replacement from same sender") {
            roomTimelineStore.addRelation(TimelineEventRelation(room, EventId("2"), RelationType.Replace, EventId("1")))
            roomTimelineStore.addRelation(TimelineEventRelation(room, EventId("3"), RelationType.Replace, EventId("1")))
            roomTimelineStore.addRelation(TimelineEventRelation(room, EventId("4"), RelationType.Replace, EventId("1")))

            cut.getTimelineEventReplaceAggregation(room, EventId("1")).first() shouldBe
                    TimelineEventAggregation.Replace(EventId("3"), listOf(EventId("2"), EventId("3")))
        }
        should("fallback to server aggregation when newer") {
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent(
                        "1", 1,
                        replacedBy = ServerAggregation.Replace(EventId("3"), UserId("sender", "server"), 3)
                    ),
                )
            )
            roomTimelineStore.addRelation(TimelineEventRelation(room, EventId("2"), RelationType.Replace, EventId("1")))

            cut.getTimelineEventReplaceAggregation(room, EventId("1")).first() shouldBe
                    TimelineEventAggregation.Replace(EventId("3"), listOf(EventId("2"), EventId("3")))
        }
    }
})