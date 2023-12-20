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
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
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
    lateinit var roomEventDecryptionServiceMock: RoomEventEncryptionServiceMock
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
        roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomServiceImpl(
            api = api,
            roomStore = roomStore,
            roomStateStore = roomStateStore,
            roomAccountDataStore = roomAccountDataStore,
            roomTimelineStore = roomTimelineStore,
            roomOutboxMessageStore = roomOutboxMessageStore,
            roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
            mediaService = mediaServiceMock,
            forgetRoomService = {},
            userInfo = simpleUserInfo,
            timelineEventHandler = TimelineEventHandlerMock(),
            config = MatrixClientConfiguration(),
            typingEventHandler = TypingEventHandler(api),
            currentSyncState = CurrentSyncState(currentSyncState),
            scope = scope
        )
    }

    afterTest {
        scope.cancel()
    }

    fun timelineEvent(
        id: String,
        originTimestamp: Long = 1234,
        sender: UserId = UserId("sender", "server"),
        replacedBy: ServerAggregation.Replace? = null,
        eventContent: MessageEventContent = RoomMessageEventContent.TextMessageEventContent(id)
    ): TimelineEvent =
        TimelineEvent(
            event = MessageEvent(
                eventContent,
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
    context(RoomService::getTimelineEventReactionAggregation.name) {
        beforeTest {
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent("1", 1),
                    timelineEvent(
                        "2", 2, UserId("2", "server"),
                        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
                    ),
                    timelineEvent(
                        "3", 3, UserId("2", "server"),
                        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
                    ),
                    timelineEvent(
                        "4", 4, UserId("2", "server"),
                        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "🦄"))
                    ),
                    timelineEvent(
                        "5", 5,
                        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
                    )
                )
            )
        }
        should("load reactions") {
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    room,
                    EventId("2"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    room,
                    EventId("3"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    room,
                    EventId("4"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    room,
                    EventId("5"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )

            cut.getTimelineEventReactionAggregation(room, EventId("1")).first() shouldBe
                    TimelineEventAggregation.Reaction(
                        mapOf(
                            "👍" to setOf(UserId("sender", "server"), UserId("2", "server")),
                            "🦄" to setOf(UserId("2", "server"))
                        )
                    )
        }
    }
})