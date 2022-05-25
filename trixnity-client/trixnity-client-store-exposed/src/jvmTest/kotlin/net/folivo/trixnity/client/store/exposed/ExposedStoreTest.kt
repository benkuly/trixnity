package net.folivo.trixnity.client.store.exposed

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.RoomTimelineKey
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedStoreTest : ShouldSpec({
    lateinit var cut: ExposedStore
    lateinit var scope: CoroutineScope
    lateinit var database: Database
    beforeTest {
        scope = CoroutineScope(Dispatchers.IO)
        database = createDatabase()
        newSuspendedTransaction(Dispatchers.IO, database) {
            SchemaUtils.create(ExposedRoomTimelineEvent)
        }
        cut = ExposedStore(
            database = database,
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixEventJson(),
            transactionDispatcher = Dispatchers.IO,
            scope = scope
        )
    }
    afterTest {
        scope.cancel()
    }
    context("transaction") {
        should("rollback transaction on exception") {
            val timelineEvent1 = TimelineEvent(
                event = Event.MessageEvent(
                    TextMessageEventContent("hi"), EventId("$1e"), UserId("sender", "server"),
                    RoomId("room", "server"), 1234
                ),
                roomId = RoomId("room", "server"),
                eventId = EventId("$1e"),
                previousEventId = null,
                nextEventId = EventId("$2e"),
                gap = null
            )
            val timelineEvent2 = TimelineEvent(
                event = Event.MessageEvent(
                    TextMessageEventContent("ok"), EventId("$2e"), UserId("sender", "server"),
                    RoomId("room", "server"), 1234
                ),
                roomId = RoomId("room", "server"),
                eventId = EventId("$2e"),
                previousEventId = EventId("$1e"),
                nextEventId = null,
                gap = TimelineEvent.Gap.GapAfter("batch")
            )
            newSuspendedTransaction {
                cut.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2))
            }
            val timelineRepo = ExposedRoomTimelineEventRepository(createMatrixEventJson())
            newSuspendedTransaction {
                timelineRepo.get(RoomTimelineKey(EventId("$1e"), RoomId("room", "server")))
                    .shouldBe(timelineEvent1)
                timelineRepo.get(RoomTimelineKey(EventId("$2e"), RoomId("room", "server")))
                    .shouldBe(timelineEvent2)
            }

            shouldThrow<IllegalArgumentException> {
                cut.transaction {
                    cut.roomTimeline.update(
                        EventId("$1e"),
                        RoomId("room", "server"),
                        withTransaction = false
                    ) {
                        timelineEvent1.copy(nextEventId = EventId("$3e"))
                    }
                    cut.roomTimeline.update(
                        EventId("$2e"),
                        RoomId("room", "server"),
                        withTransaction = false
                    ) {
                        timelineEvent2.copy(gap = null)
                    }
                    throw IllegalArgumentException("oh no")
                }
            }
            newSuspendedTransaction {
                cut.roomTimeline.get(EventId("$1e"), RoomId("room", "server")) shouldBe timelineEvent1
                cut.roomTimeline.get(EventId("$2e"), RoomId("room", "server")) shouldBe timelineEvent2
            }
            newSuspendedTransaction {
                timelineRepo.get(RoomTimelineKey(EventId("$1e"), RoomId("room", "server")))
                    .shouldBe(timelineEvent1)
                timelineRepo.get(RoomTimelineKey(EventId("$2e"), RoomId("room", "server")))
                    .shouldBe(timelineEvent2)
            }
        }
    }
})