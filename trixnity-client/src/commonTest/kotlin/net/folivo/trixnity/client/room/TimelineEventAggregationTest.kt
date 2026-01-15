package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEventRelation
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test

class TimelineEventAggregationTest : TrixnityBaseTest() {

    private val room = simpleRoom.roomId

    private val roomTimelineStore = getInMemoryRoomTimelineStore()

    private val mediaServiceMock = MediaServiceMock()
    private val roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock()

    private val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    private val api = mockMatrixClientServerApiClient()

    private val cut = RoomServiceImpl(
        api = api,
        roomStore = getInMemoryRoomStore(),
        roomStateStore = getInMemoryRoomStateStore(),
        roomAccountDataStore = getInMemoryRoomAccountDataStore(),
        roomTimelineStore = roomTimelineStore,
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(),
        roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
        mediaService = mediaServiceMock,
        forgetRoomService = { _, _ -> },
        userInfo = simpleUserInfo,
        timelineEventHandler = TimelineEventHandlerMock(),
        clock = testScope.testClock,
        config = MatrixClientConfiguration(),
        typingEventHandler = TypingEventHandlerImpl(api),
        currentSyncState = CurrentSyncState(currentSyncState),
        scope = testScope.backgroundScope
    )

    private fun timelineEvent(
        id: String,
        originTimestamp: Long = 1234,
        sender: UserId = UserId("sender", "server"),
        replacedBy: ServerAggregation.Replace? = null,
        eventContent: MessageEventContent = RoomMessageEventContent.TextBased.Text(id)
    ): TimelineEvent =
        TimelineEvent(
            event = MessageEvent(
                eventContent,
                EventId(id),
                sender,
                room,
                originTimestamp,
                UnsignedRoomEventData.UnsignedMessageEventData(relations = Relations(buildMap {
                    replacedBy?.also { put(RelationType.Replace, it) }
                }))
            ),
            gap = null,
            nextEventId = null,
            previousEventId = null,
        )

    @Test
    fun `use latest replacement from same sender`() = runTest {
        getTimelineEventReplaceAggregationSetup()
        roomTimelineStore.apply {
            addRelation(TimelineEventRelation(room, EventId("2"), RelationType.Replace, EventId("1")))
            addRelation(TimelineEventRelation(room, EventId("3"), RelationType.Replace, EventId("1")))
            addRelation(TimelineEventRelation(room, EventId("4"), RelationType.Replace, EventId("1")))
        }

        cut.getTimelineEventReplaceAggregation(room, EventId("1")).first() shouldBe
                TimelineEventAggregation.Replace(EventId("3"), listOf(EventId("2"), EventId("3")))
    }

    @Test
    fun `fallback to server aggregation when newer`() = runTest {
        getTimelineEventReplaceAggregationSetup()
        roomTimelineStore.apply {
            addAll(
                listOf(
                    timelineEvent(
                        "1", 1,
                        replacedBy = ServerAggregation.Replace(EventId("3"), UserId("sender", "server"), 3)
                    ),
                )
            )
            addRelation(TimelineEventRelation(room, EventId("2"), RelationType.Replace, EventId("1")))
        }

        cut.getTimelineEventReplaceAggregation(room, EventId("1")).first() shouldBe
                TimelineEventAggregation.Replace(EventId("3"), listOf(EventId("2"), EventId("3")))
    }

    private val originalEvent = timelineEvent("1", 1)
    private val reactionUser2Thumbs1 = timelineEvent(
        "2", 2, UserId("2", "server"),
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
    )
    private val reactionUser2Thumbs2 = timelineEvent(
        "3", 3, UserId("2", "server"),
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
    )
    private val reactionUser2Unicorn = timelineEvent(
        "4", 4, UserId("2", "server"),
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "🦄"))
    )
    private val reactionUser1Thumbs = timelineEvent(
        "5", 5,
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
    )
    private val reactionUser3Thumbs = timelineEvent(
        "6", 6, UserId("3", "server"),
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "👍"))
    )
    private val reactionUser3Dog = timelineEvent(
        "7", 7, UserId("3", "server"),
        eventContent = ReactionEventContent(RelatesTo.Annotation(EventId("1"), "🐈"))
    )

    @Test
    fun `getTimelineEventReactionAggregation » load reactions`() = runTest {
        getTimelineEventReactionAggregationSetup()
        cut.getTimelineEventReactionAggregation(room, EventId("1")).first() shouldBe
                TimelineEventAggregation.Reaction(
                    mapOf(
                        "👍" to setOf(
                            reactionUser1Thumbs,
                            reactionUser2Thumbs2,
                        ),
                        "🦄" to setOf(
                            reactionUser2Unicorn,
                        )
                    )
                )
    }

    @Test
    fun `getTimelineEventReactionAggregation » update reactions on change`() = runTest {
        getTimelineEventReactionAggregationSetup()
        val receivedValues = MutableStateFlow(0)
        val result = async {
            cut.getTimelineEventReactionAggregation(room, EventId("1"))
                .onEach { receivedValues.value++ }
                .take(3).toList()
        }

        receivedValues.first { it == 1 }

        with(roomTimelineStore) {
            addAll(
                listOf(
                    reactionUser3Thumbs
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("6"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )

            receivedValues.first { it == 2 }
            addAll(
                listOf(
                    reactionUser3Dog
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("7"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
        }

        result.await() shouldBe listOf(
            TimelineEventAggregation.Reaction(
                mapOf(
                    "👍" to setOf(
                        reactionUser1Thumbs,
                        reactionUser2Thumbs2,
                    ),
                    "🦄" to setOf(
                        reactionUser2Unicorn,
                    )
                )
            ),
            TimelineEventAggregation.Reaction(
                mapOf(
                    "👍" to setOf(
                        reactionUser1Thumbs,
                        reactionUser2Thumbs2,
                        reactionUser3Thumbs
                    ),
                    "🦄" to setOf(
                        reactionUser2Unicorn,
                    )
                )
            ),
            TimelineEventAggregation.Reaction(
                mapOf(
                    "👍" to setOf(
                        reactionUser1Thumbs,
                        reactionUser2Thumbs2,
                        reactionUser3Thumbs
                    ),
                    "🦄" to setOf(
                        reactionUser2Unicorn,
                    ),
                    "🐈" to setOf(
                        reactionUser3Dog
                    )
                )
            ),
        )
    }


    private suspend fun getTimelineEventReplaceAggregationSetup() {
        with(roomTimelineStore) {
            addAll(
                listOf(
                    timelineEvent("1", 1),
                    timelineEvent("2", 2),
                    timelineEvent("3", 3),
                    timelineEvent("4", 4, UserId("otherSender", "server"))
                )
            )
        }
    }

    private suspend fun getTimelineEventReactionAggregationSetup() {
        with(roomTimelineStore) {
            addAll(
                listOf(
                    originalEvent,
                    reactionUser2Thumbs1,
                    reactionUser2Thumbs2,
                    reactionUser2Unicorn,
                    reactionUser1Thumbs
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("2"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("3"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("4"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
            addRelation(
                TimelineEventRelation(
                    room,
                    EventId("5"),
                    RelationType.Annotation,
                    EventId("1")
                )
            )
        }
    }
}
