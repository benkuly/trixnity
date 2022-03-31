package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARD
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.keys.Key

class RoomServiceTimelineUtilsTest : ShouldSpec({
    timeout = 5_000

    val room = RoomId("room", "server")
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var scope: CoroutineScope
    val api = mockk<MatrixClientServerApiClient>()
    val olm = mockk<OlmService>()
    val currentSyncState = MutableStateFlow(SyncApiClient.SyncState.RUNNING)

    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        val key = mockk<KeyService>(relaxed = true)
        cut = RoomService(UserId("alice", "server"), store, api, olm, key, mockk(), mockk(), currentSyncState)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
        scope.cancel()
    }

    fun encryptedEvent(i: Long = 24): MessageEvent<MegolmEncryptedEventContent> {
        return MessageEvent(
            MegolmEncryptedEventContent(
                ciphertext = "cipher $i",
                deviceId = "deviceId",
                sessionId = "senderId",
                senderKey = Key.Curve25519Key(value = "key")
            ),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }

    val event1 = encryptedEvent(1)
    val event2 = encryptedEvent(2)
    val event3 = encryptedEvent(3)
    val timelineEvent1 = TimelineEvent(
        event = event1,
        roomId = room,
        eventId = event1.id,
        previousEventId = null,
        nextEventId = event2.id,
        gap = TimelineEvent.Gap.GapBefore("1")
    )
    val timelineEvent2 = TimelineEvent(
        event = event2,
        roomId = room,
        eventId = event2.id,
        previousEventId = event1.id,
        nextEventId = event3.id,
        gap = null
    )
    val timelineEvent3 = TimelineEvent(
        event = event3,
        roomId = room,
        eventId = event3.id,
        previousEventId = event2.id,
        nextEventId = null,
        gap = TimelineEvent.Gap.GapAfter("3")
    )
    context(RoomService::getTimelineEvents.name) {
        context("all requested events in store") {
            beforeTest {
                store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
            }
            should("get timeline events backwards") {
                cut.getTimelineEvents(cut.getTimelineEvent(event3.id, room, scope), scope = scope)
                    .take(3).toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1
                )
            }
            should("get timeline events forwards") {
                cut.getTimelineEvents(cut.getTimelineEvent(event1.id, room, scope), FORWARD, scope = scope)
                    .take(3).toList().map { it.value } shouldBe listOf(
                    timelineEvent1,
                    timelineEvent2,
                    timelineEvent3
                )
            }
        }
        context("not all events in store") {
            val event0 = encryptedEvent(0)
            val timelineEvent0 = TimelineEvent(
                event = event0,
                roomId = room,
                eventId = event0.id,
                previousEventId = null,
                nextEventId = event1.id,
                gap = TimelineEvent.Gap.GapBefore("0")
            )
            beforeTest {
                store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
                coEvery {
                    api.rooms.getEvents(
                        roomId = room,
                        from = "1",
                        dir = BACKWARDS,
                        limit = 20,
                        filter = """{"lazy_load_members":true}"""
                    )
                } returns Result.success(
                    GetEvents.Response(
                        start = "1",
                        end = "0",
                        chunk = listOf(event0),
                        state = listOf()
                    )
                )
            }
            should("fetch mssing events from server") {
                cut.getTimelineEvents(cut.getTimelineEvent(event3.id, room, scope), scope = scope)
                    .take(4).toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1.copy(gap = null, previousEventId = event0.id),
                    timelineEvent0
                )
            }
        }
        context("complete timeline in store") {
            beforeTest {
                store.roomTimeline.addAll(
                    listOf(
                        timelineEvent1.copy(gap = null, previousEventId = null),
                        timelineEvent2,
                        timelineEvent3
                    )
                )
            }
            should("flow should be finished when all collected") {
                cut.getTimelineEvents(cut.getTimelineEvent(event3.id, room, scope), scope = scope)
                    .toList().map { it.value } shouldBe listOf(
                    timelineEvent3,
                    timelineEvent2,
                    timelineEvent1.copy(gap = null, previousEventId = null)
                )
            }
        }
    }
    context(RoomService::getLastTimelineEvents.name) {
        beforeTest {
            store.roomTimeline.addAll(listOf(timelineEvent1, timelineEvent2, timelineEvent3))
        }
        should("get timeline events") {
            store.room.update(room) { Room(roomId = room, lastEventId = event3.id) }
            cut.getLastTimelineEvents(room, scope = scope)
                .first()
                .shouldNotBeNull()
                .take(3).toList().map { it.value } shouldBe listOf(
                timelineEvent3,
                timelineEvent2,
                timelineEvent1
            )
            scope.coroutineContext.job.children.count() shouldBe 1
        }
        should("cancel old timeline event flow") {
            store.room.update(room) { Room(roomId = room, lastEventId = event2.id) }
            val collectedEvents = mutableListOf<TimelineEvent?>()
            cut.getLastTimelineEvents(room, scope = scope).take(2)
                .filterNotNull()
                .collectLatest { timelineEventFlow ->
                    collectedEvents.addAll(timelineEventFlow.take(2).toList().map { it.value })
                    store.room.update(room) { Room(roomId = room, lastEventId = event3.id) }
                }

            collectedEvents shouldBe listOf(
                // first collect
                timelineEvent2,
                timelineEvent1,
                // second collect
                timelineEvent3,
                timelineEvent2,
            )
            scope.coroutineContext.job.children.count() shouldBe 1
        }
    }
})