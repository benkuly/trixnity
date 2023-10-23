package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class TimelineEventHandlerTest : ShouldSpec({
    timeout = 10_000
    val alice = UserId("alice", "server")
    val room = RoomId("room", "server")
    lateinit var roomStore: RoomStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope
    lateinit var apiConfig: PortableMockEngineConfig
    val json = createMatrixEventJson()

    lateinit var cut: TimelineEventHandlerImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = TimelineEventHandlerImpl(
            api,
            roomStore, roomTimelineStore, roomOutboxMessageStore,
            TimelineMutex(),
            RepositoryTransactionManagerMock(),
        )
    }

    afterTest {
        scope.cancel()
    }

    suspend fun storeTimeline(vararg events: RoomEvent<*>) = events.map {
        roomTimelineStore.get(it.id, it.roomId).first()
    }

    fun nameEvent(i: Long = 60): StateEvent<NameEventContent> {
        return StateEvent(
            NameEventContent("The room name"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i,
            stateKey = ""
        )
    }

    fun textEvent(i: Long = 24): MessageEvent<TextMessageEventContent> {
        return MessageEvent(
            TextMessageEventContent("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }
    context(TimelineEventHandlerImpl::redactTimelineEvent.name) {
        context("with existent event") {
            should("redact room event") {
                val event1 = textEvent(1)
                val event2 = textEvent(2)
                val event3 = textEvent(3)
                roomTimelineStore.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            content = null,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            content = Result.failure(DecryptionException.ValidationFailed("")),
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            content = null,
                            roomId = room,
                            eventId = event3.id,
                            previousEventId = event3.id,
                            nextEventId = null,
                            gap = null
                        )
                    )
                )
                val redactionEvent = MessageEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = event2.id),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                assertSoftly(roomTimelineStore.get(event2.id, room).first().shouldNotBeNull()) {
                    event shouldBe MessageEvent(
                        RedactedEventContent("m.room.message"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedRoomEventData.UnsignedMessageEventData(
                            redactedBecause = redactionEvent
                        )
                    )
                    content shouldBe Result.success(RedactedEventContent("m.room.message"))
                    roomId shouldBe room
                    eventId shouldBe event2.id
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                }
            }
            should("redact state event") {
                val event1 = nameEvent(1)
                val event2 = nameEvent(2)
                val event3 = nameEvent(3)
                roomTimelineStore.addAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            content = null,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            content = Result.failure(DecryptionException.ValidationFailed("")),
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            content = null,
                            roomId = room,
                            eventId = event3.id,
                            previousEventId = event3.id,
                            nextEventId = null,
                            gap = null
                        )
                    )
                )
                val redactionEvent = MessageEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = event2.id),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                assertSoftly(roomTimelineStore.get(event2.id, room).first().shouldNotBeNull()) {
                    event shouldBe StateEvent(
                        RedactedEventContent("m.room.name"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedRoomEventData.UnsignedStateEventData(
                            redactedBecause = redactionEvent
                        ),
                        ""
                    )
                    content shouldBe Result.success(RedactedEventContent("m.room.name"))
                    roomId shouldBe room
                    eventId shouldBe event2.id
                    previousEventId shouldBe event1.id
                    nextEventId shouldBe event3.id
                }
            }
        }
        context("with nonexistent event") {
            should("do nothing") {
                val event1 = nameEvent(1)
                val event2 = nameEvent(2)
                val timelineEvent1 = TimelineEvent(
                    event = event1,
                    content = null,
                    roomId = room,
                    eventId = event1.id,
                    previousEventId = null,
                    nextEventId = event2.id,
                    gap = null
                )
                val timelineEvent2 = TimelineEvent(
                    event = event2,
                    content = Result.failure(DecryptionException.ValidationFailed("")),
                    roomId = room,
                    eventId = event2.id,
                    previousEventId = event1.id,
                    nextEventId = null,
                    gap = null
                )
                roomTimelineStore.addAll(
                    listOf(
                        timelineEvent1,
                        timelineEvent2,
                    )
                )

                val redactionEvent = MessageEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = EventId("\$incorrectlyEvent")),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                roomTimelineStore.get(EventId("\$incorrectlyEvent"), room).first() shouldBe null
                roomTimelineStore.get(timelineEvent1.eventId, room).first() shouldBe timelineEvent1
                roomTimelineStore.get(timelineEvent2.eventId, room).first() shouldBe timelineEvent2
            }
        }
    }
    context(TimelineEventHandlerImpl::addEventsToTimelineAtEnd.name) {
        val event1 = plainEvent(1)
        val event2 = plainEvent(2)
        val event3 = plainEvent(3)
        context("initial sync") {
            should("add elements to timeline") {
                roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), null, "next", false)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        +event1
                        +event2
                        +event3
                        gap("next")
                    }
                }
            }
            should("add elements to timeline with gap") {
                roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), "prev", "next", true)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        gap("prev")
                        +event1
                        +event2
                        +event3
                        gap("next")
                    }
                }
            }
            should("add one element to timeline") {
                roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                cut.addEventsToTimelineAtEnd(room, listOf(event1), null, "next", false)
                storeTimeline(event1) shouldContainExactly timeline {
                    fragment {
                        +event1
                        gap("next")
                    }
                }
            }
        }
        context("without gap") {
            should("add elements to timeline") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                roomTimelineStore.addAll(
                    timeline {
                        fragment {
                            +event1
                            gap("oldPrevious")
                        }
                    }
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", "next", false)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        +event1
                        +event2
                        +event3
                        gap("next")
                    }
                }
            }
            should("add elements to gappy timeline") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                roomTimelineStore.addAll(
                    timeline {
                        fragment {
                            gap("before")
                            +event1
                            gap("oldPrevious")
                        }
                    }
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", "next", false)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        gap("before")
                        +event1
                        +event2
                        +event3
                        gap("next")
                    }
                }
            }
            should("add one element to timeline") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                roomTimelineStore.addAll(
                    timeline {
                        fragment {
                            +event1
                            gap("oldPrevious")
                        }
                    }
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", "next", false)
                storeTimeline(event1, event3) shouldContainExactly timeline {
                    fragment {
                        +event1
                        +event3
                        gap("next")
                    }
                }
            }
            should("add one element to timeline that already exists") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                roomTimelineStore.addAll(
                    timeline {
                        fragment {
                            +event1
                            +event2
                            +event3
                            gap("oldPrevious")
                        }
                    }
                )
                cut.addEventsToTimelineAtEnd(room, listOf(event2), "previous", "next", false)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        +event1
                        +event2
                        +event3
                        gap("oldPrevious")
                    }
                }
            }
            should("filter duplicate events") {
                roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                cut.addEventsToTimelineAtEnd(room, listOf(event1, event1), "previous", "next", false)
                storeTimeline(event1) shouldContainExactly timeline {
                    fragment {
                        +event1
                        gap("next")
                    }
                }
            }
        }
        context("with gap") {
            context("without previous events") {
                should("add elements to timeline") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1, event2, event3), "previous", "next", true)
                    storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                        fragment {
                            gap("previous")
                            +event1
                            +event2
                            +event3
                            gap("next")
                        }
                    }
                }
                should("add one element to timeline") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = null) }
                    cut.addEventsToTimelineAtEnd(room, listOf(event1), "previous", "next", true)
                    storeTimeline(event1) shouldContainExactly timeline {
                        fragment {
                            gap("previous")
                            +event1
                            gap("next")
                        }
                    }
                }
            }
            context("with previous events") {
                should("add elements to gappy timeline") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    roomTimelineStore.addAll(
                        timeline {
                            fragment {
                                gap("oldPrevious-1")
                                +event1
                                gap("oldPrevious")
                            }
                        }
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", "next", true)
                    storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                        fragment {
                            gap("oldPrevious-1")
                            +event1
                            gap("oldPrevious")
                            gap("previous")
                            +event2
                            +event3
                            gap("next")
                        }
                    }
                }
                should("add elements to timeline") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    roomTimelineStore.addAll(
                        timeline {
                            fragment {
                                +event1
                                gap("oldPrevious")
                            }
                        }
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", "next", true)
                    storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                        fragment {
                            +event1
                            gap("oldPrevious")
                            gap("previous")
                            +event2
                            +event3
                            gap("next")
                        }
                    }
                }
                should("add elements to timeline with existing gap") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    roomTimelineStore.addAll(
                        timeline {
                            fragment {
                                gap("before")
                                +event1
                                gap("oldPrevious")
                            }
                        }
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event2, event3), "previous", "next", true)
                    storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                        fragment {
                            gap("before")
                            +event1
                            gap("oldPrevious")
                            gap("previous")
                            +event2
                            +event3
                            gap("next")
                        }
                    }
                }
                should("add one element to timeline") {
                    roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    roomTimelineStore.addAll(
                        timeline {
                            fragment {
                                +event1
                                gap("oldPrevious")
                            }
                        }
                    )
                    cut.addEventsToTimelineAtEnd(room, listOf(event3), "previous", "next", true)
                    storeTimeline(event1, event3) shouldContainExactly timeline {
                        fragment {
                            +event1
                            gap("oldPrevious")
                            gap("previous")
                            +event3
                            gap("next")
                        }
                    }
                }
            }
        }
        context("outbox messages") {
            should("be used to instantly decrypt received encrypted timeline events that have same transaction id") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
                roomOutboxMessageStore.update("transactionId1") {
                    RoomOutboxMessage(
                        "transactionId1",
                        room,
                        TextMessageEventContent("Hello!")
                    )
                }
                val eventId1 = EventId("\$event1")
                val eventId2 = EventId("\$event2")
                val eventId3 = EventId("\$event3")
                val encryptedEvent1 = MessageEvent(
                    MegolmEncryptedEventContent("foobar", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId1,
                    UserId("sender", "server"),
                    room,
                    0L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId1")
                )
                val encryptedEvent2 = MessageEvent(
                    MegolmEncryptedEventContent("barfoo", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId2,
                    UserId("other", "server"),
                    room,
                    10L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId2")
                )
                val encryptedEvent3 = MessageEvent(
                    MegolmEncryptedEventContent("foo", Key.Curve25519Key(value = "key"), "deviceId", "sessionId"),
                    eventId3,
                    UserId("sender", "server"),
                    room,
                    20L,
                    UnsignedRoomEventData.UnsignedMessageEventData(transactionId = "transactionId-unknown")
                )
                cut.addEventsToTimelineAtEnd(
                    room,
                    listOf(encryptedEvent1, encryptedEvent2, encryptedEvent3),
                    "previous",
                    "next",
                    false
                )

                assertSoftly(roomTimelineStore.get(eventId1, room).first().shouldNotBeNull()) {
                    content shouldBe Result.success(TextMessageEventContent("Hello!"))
                }
                assertSoftly(roomTimelineStore.get(eventId2, room).first().shouldNotBeNull()) {
                    content shouldBe null
                }
                assertSoftly(roomTimelineStore.get(eventId3, room).first().shouldNotBeNull()) {
                    content shouldBe null
                }
            }
        }
    }
    context(TimelineEventHandlerImpl::handleSyncResponse.name) {
        beforeTest {
            roomStore.update(room) { simpleRoom.copy(roomId = room) }
        }
        context("lastEventId") {
            should("set lastEventId from room event") {
                cut.handleSyncResponse(
                    SyncEvents(
                        Sync.Response(
                            nextBatch = "",
                            room = Sync.Response.Rooms(
                                join = mapOf(
                                    room to Sync.Response.Rooms.JoinedRoom(
                                        timeline = Sync.Response.Rooms.Timeline(listOf(textEvent(24)))
                                    )
                                ),
                            )
                        ),
                        emptyList()
                    )
                )
                roomStore.get(room).first()?.lastEventId shouldBe EventId("\$event24")
            }
            should("set lastEventId from state event") {
                cut.handleSyncResponse(
                    SyncEvents(
                        Sync.Response(
                            nextBatch = "",
                            room = Sync.Response.Rooms(
                                join = mapOf(
                                    room to Sync.Response.Rooms.JoinedRoom(
                                        timeline = Sync.Response.Rooms.Timeline(
                                            listOf(
                                                StateEvent(
                                                    MemberEventContent(membership = Membership.JOIN),
                                                    EventId("\$event24"),
                                                    alice,
                                                    room,
                                                    25,
                                                    stateKey = alice.full
                                                )
                                            )
                                        )
                                    )
                                ),
                            )
                        ),
                        emptyList()
                    )
                )
                roomStore.get(room).first()?.lastEventId shouldBe EventId("\$event24")
            }
        }
    }
    context(TimelineEventHandlerImpl::unsafeFillTimelineGaps.name) {
        val event1 = plainEvent(1)
        val event2 = plainEvent(2)
        val event3 = plainEvent(3)
        val event4 = plainEvent(4)
        val event5 = plainEvent(5)
        context("start event does exist in store") {
            context("start event has previous gap") {
                should("add elements to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            GetEvents(
                                room,
                                "start",
                                dir = GetEvents.Direction.BACKWARDS,
                                limit = 20,
                                filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2, event1),
                                state = listOf()
                            )
                        }
                    }
                    roomTimelineStore.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                        }
                    })
                    cut.unsafeFillTimelineGaps(event3.id, room)
                    storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                        fragment {
                            gap("end")
                            +event1
                            +event2
                            +event3
                        }
                    }
                }
                should("add one element to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            GetEvents(
                                room,
                                "start",
                                dir = GetEvents.Direction.BACKWARDS,
                                limit = 20,
                                filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event2),
                                state = listOf()
                            )
                        }
                    }
                    roomTimelineStore.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                        }
                    })
                    cut.unsafeFillTimelineGaps(event3.id, room)
                    storeTimeline(event2, event3) shouldContainExactly timeline {
                        fragment {
                            gap("end")
                            +event2
                            +event3
                        }
                    }
                }
                should("detect start of timeline when start and end are the same") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            GetEvents(
                                room,
                                "start",
                                dir = GetEvents.Direction.BACKWARDS,
                                limit = 20,
                                filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "start",
                                chunk = listOf(),
                                state = listOf()
                            )
                        }
                    }
                    roomTimelineStore.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                            +event4
                            gap("after")
                        }
                    })
                    cut.unsafeFillTimelineGaps(event3.id, room)
                    storeTimeline(event3, event4) shouldContainExactly timeline {
                        fragment {
                            +event3
                            +event4
                            gap("after")
                        }
                    }
                }
                should("detect start of timeline when end is null") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            GetEvents(
                                room,
                                "start",
                                dir = GetEvents.Direction.BACKWARDS,
                                limit = 20,
                                filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = null,
                                chunk = listOf(),
                                state = listOf()
                            )
                        }
                    }
                    roomTimelineStore.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                            +event4
                            gap("after")
                        }
                    })
                    cut.unsafeFillTimelineGaps(event3.id, room)
                    storeTimeline(event3, event4) shouldContainExactly timeline {
                        fragment {
                            +event3
                            +event4
                            gap("after")
                        }
                    }
                }
                context("gap filled") {
                    should("add element to timeline") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start-3",
                                    "end-1",
                                    dir = GetEvents.Direction.BACKWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start-3",
                                    end = "end-1",
                                    chunk = listOf(event2),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                +event2
                                +event3
                            }
                        }
                    }
                    should("add element to timeline when end is null") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start-3",
                                    "end-1",
                                    dir = GetEvents.Direction.BACKWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start-3",
                                    end = null,
                                    chunk = listOf(event2),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                +event2
                                +event3
                            }
                        }
                    }
                    should("ignore overlapping events") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start-3",
                                    "end-1",
                                    dir = GetEvents.Direction.BACKWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start",
                                    end = "end",
                                    chunk = listOf(event2, event1.copy(originTimestamp = 24)),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                +event2
                                +event3
                            }
                        }
                    }
                    should("prevent loop") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "gap",
                                    null,
                                    dir = GetEvents.Direction.BACKWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "gap",
                                    end = null,
                                    chunk = listOf(event3, event2, event1),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("gap")
                                +event1
                                +event2
                                +event3
                                gap("gap")
                            }
                        })
                        cut.unsafeFillTimelineGaps(event1.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                +event1
                                +event2
                                +event3
                                gap("gap")
                            }
                        }
                    }
                }
                context("gap not filled") {
                    should("add element to timeline") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start-3",
                                    "end-1",
                                    dir = GetEvents.Direction.BACKWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start-3",
                                    end = "start-2",
                                    chunk = listOf(event2),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-2")
                                +event2
                                +event3
                            }
                        }
                    }
                }
            }
            context("start event has next gap") {
                should("add elements to timeline") {
                    apiConfig.endpoints {
                        matrixJsonEndpoint(
                            GetEvents(
                                room,
                                "start",
                                dir = GetEvents.Direction.FORWARDS,
                                limit = 20,
                                filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                            )
                        ) {
                            GetEvents.Response(
                                start = "start",
                                end = "end",
                                chunk = listOf(event4),
                                state = listOf()
                            )
                        }
                    }
                    roomTimelineStore.addAll(timeline {
                        fragment {
                            +event3
                            gap("start")
                        }
                    })
                    cut.unsafeFillTimelineGaps(event3.id, room)
                    storeTimeline(event3, event4) shouldContainExactly timeline {
                        fragment {
                            +event3
                            +event4
                            gap("end")
                        }
                    }
                }
                context("gap filled") {
                    should("add elements to timeline") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start",
                                    "end",
                                    dir = GetEvents.Direction.FORWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start",
                                    end = "end",
                                    chunk = listOf(event3, event4),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                gap("start")
                                gap("end")
                                +event5
                            }
                        })
                        cut.unsafeFillTimelineGaps(event2.id, room)
                        storeTimeline(event1, event2, event3, event4, event5) shouldContainExactly timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                +event3
                                +event4
                                +event5
                            }
                        }
                    }
                    should("add elements to timeline when end is null") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start",
                                    "end",
                                    dir = GetEvents.Direction.FORWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start",
                                    end = null,
                                    chunk = listOf(event3, event4),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                gap("start")
                                gap("end")
                                +event5
                            }
                        })
                        cut.unsafeFillTimelineGaps(event2.id, room)
                        storeTimeline(event1, event2, event3, event4, event5) shouldContainExactly timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                +event3
                                +event4
                                +event5
                            }
                        }
                    }
                    should("ignore overlapping events") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start",
                                    "end",
                                    dir = GetEvents.Direction.FORWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start",
                                    end = "end",
                                    chunk = listOf(event4, event5.copy(originTimestamp = 24)),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                +event3
                                gap("start")
                                gap("end")
                                +event5
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3, event4, event5) shouldContainExactly timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                +event3
                                +event4
                                +event5
                            }
                        }
                    }
                }
                context("gap not filled") {
                    should("add element to timeline") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                GetEvents(
                                    room,
                                    "start",
                                    "next",
                                    dir = GetEvents.Direction.FORWARDS,
                                    limit = 20,
                                    filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                                )
                            ) {
                                GetEvents.Response(
                                    start = "start",
                                    end = "end",
                                    chunk = listOf(event4),
                                    state = listOf()
                                )
                            }
                        }
                        roomTimelineStore.addAll(timeline {
                            fragment {
                                gap("gap-before")
                                +event2
                                +event3
                                gap("start")
                                gap("next")
                                +event5
                                gap("next-1")
                            }
                        })
                        cut.unsafeFillTimelineGaps(event3.id, room)
                        storeTimeline(event2, event3, event4, event5) shouldContainExactly timeline {
                            fragment {
                                gap("gap-before")
                                +event2
                                +event3
                                +event4
                                gap("end")
                                gap("next")
                                +event5
                                gap("next-1")
                            }
                        }
                    }
                }
            }
            should("only fetch event before, when last event of room") {
                roomStore.update(room) { Room(roomId = room, lastEventId = event3.id, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "start",
                            dir = GetEvents.Direction.BACKWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "start",
                            end = "end",
                            chunk = listOf(event2, event1),
                            state = listOf()
                        )
                    }
                }
                roomTimelineStore.addAll(timeline {
                    fragment {
                        gap("start")
                        +event3
                        gap("next")
                    }
                })
                cut.unsafeFillTimelineGaps(event3.id, room)
                storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                    fragment {
                        gap("end")
                        +event1
                        +event2
                        +event3
                        gap("next")
                    }
                }
            }
            should("should detect loop due to event found in chunk") {
                roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "before-2",
                            "after-1",
                            dir = GetEvents.Direction.BACKWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "before-2",
                            end = "before-1",
                            chunk = listOf(event4, event1),
                            state = listOf()
                        )
                    }
                }
                roomTimelineStore.addAll(timeline {
                    fragment {
                        gap("before-1")
                        +event1
                        gap("after-1")
                        gap("before-2")
                        +event2
                        +event3
                        +event4
                        gap("after-4")
                        gap("before-5")
                        +event5
                        gap("after-5")
                    }
                })
                cut.unsafeFillTimelineGaps(event2.id, room)
                storeTimeline(event1, event2, event3, event4, event5) shouldContainExactly timeline {
                    fragment {
                        gap("before-1")
                        +event1
                        +event2
                        +event3
                        +event4
                        gap("after-4")
                        gap("before-5")
                        +event5
                        gap("after-5")
                    }
                }
            }
            should("should handle gap filling without new events") {
                roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "before-3",
                            "after-2",
                            dir = GetEvents.Direction.BACKWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "before-3",
                            end = "after-2",
                            chunk = listOf(),
                            state = listOf()
                        )
                    }
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "after-3",
                            "before-4",
                            dir = GetEvents.Direction.FORWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "after-3",
                            end = "before-4",
                            chunk = listOf(),
                            state = listOf()
                        )
                    }
                }
                roomTimelineStore.addAll(timeline {
                    fragment {
                        gap("before-2")
                        +event2
                        gap("after-2")
                        gap("before-3")
                        +event3
                        gap("after-3")
                        gap("before-4")
                        +event4
                        gap("after-4")
                    }
                })
                cut.unsafeFillTimelineGaps(event3.id, room)
                storeTimeline(event2, event3, event4) shouldContainExactly timeline {
                    fragment {
                        gap("before-2")
                        +event2
                        +event3
                        +event4
                        gap("after-4")
                    }
                }
            }
            should("should handle gap filling without new events and same tokens") {
                roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "after-2",
                            "after-2",
                            dir = GetEvents.Direction.BACKWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "after-2",
                            end = "after-2",
                            chunk = listOf(),
                            state = listOf()
                        )
                    }
                    matrixJsonEndpoint(
                        GetEvents(
                            room,
                            "before-4",
                            "before-4",
                            dir = GetEvents.Direction.FORWARDS,
                            limit = 20,
                            filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                        )
                    ) {
                        GetEvents.Response(
                            start = "before-4",
                            end = "before-4",
                            chunk = listOf(),
                            state = listOf()
                        )
                    }
                }
                roomTimelineStore.addAll(timeline {
                    fragment {
                        gap("before-2")
                        +event2
                        gap("after-2")
                        +event3
                        gap("before-4")
                        +event4
                        gap("after-4")
                    }
                })
                cut.unsafeFillTimelineGaps(event3.id, room)
                storeTimeline(event2, event3, event4) shouldContainExactly timeline {
                    fragment {
                        gap("before-2")
                        +event2
                        +event3
                        +event4
                        gap("after-4")
                    }
                }
            }
        }
        should("process redactions from gaps") {
            val redactionEvent = MessageEvent(
                RedactionEventContent(redacts = EventId("\$event3")),
                EventId("\$event2"),
                UserId("sender", "server"),
                RoomId("room", "server"),
                2
            )
            val redactedEvent = MessageEvent(
                RedactedEventContent("m.room.message"),
                EventId("\$event3"),
                UserId("sender", "server"),
                RoomId("room", "server"),
                3,
                UnsignedRoomEventData.UnsignedMessageEventData(redactedBecause = redactionEvent),
            )
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                    )
                ) {
                    GetEvents.Response(
                        start = "start",
                        end = "end",
                        chunk = listOf(redactionEvent, event1),
                        state = listOf()
                    )
                }
            }
            roomTimelineStore.addAll(timeline {
                fragment {
                    gap("start")
                    +event3
                }
            })
            cut.unsafeFillTimelineGaps(event3.id, room)
            storeTimeline(event1, redactionEvent, redactedEvent) shouldContainExactly timeline {
                fragment {
                    gap("end")
                    +event1
                    +redactionEvent
                    +redactedEvent
                }
            }
        }
        should("not allow parallel insertion of events in the same room") {
            val firstEndpointCalled = MutableStateFlow(false)
            val resumeFirstEndpointCall = MutableStateFlow(false)
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "before-3",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                    )
                ) {
                    firstEndpointCalled.value = true
                    resumeFirstEndpointCall.first { it }
                    GetEvents.Response(
                        start = "before-3",
                        end = "before-2",
                        chunk = listOf(event2),
                        state = listOf()
                    )
                }
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "before-3",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = TimelineEventHandlerImpl.LAZY_LOAD_MEMBERS_FILTER
                    )
                ) {
                    GetEvents.Response(
                        start = "before-3",
                        end = "before-1",
                        chunk = listOf(event2, event1),
                        state = listOf()
                    )
                }
            }
            roomTimelineStore.addAll(timeline {
                fragment {
                    gap("before-3")
                    +event3
                }
            })
            launch {
                cut.unsafeFillTimelineGaps(event3.id, room)
            }
            firstEndpointCalled.first { it }
            val otherJob = launch(start = CoroutineStart.UNDISPATCHED) {
                cut.unsafeFillTimelineGaps(event3.id, room)
            }
            otherJob.isActive shouldBe true
            resumeFirstEndpointCall.value = true
            otherJob.join()

            storeTimeline(event2, event3) shouldContainExactly timeline {
                fragment {
                    gap("before-2")
                    +event2
                    +event3
                }
            }
        }
    }
    context(TimelineEventHandlerImpl::addRelation.name) {
        should("add relation") {
            cut.addRelation(
                MessageEvent(
                    TextMessageEventContent(
                        "hi",
                        relatesTo = RelatesTo.Reference(EventId("$1other"))
                    ),
                    EventId("$1event"),
                    UserId("sender", "server"),
                    RoomId("room", "server"),
                    1234,
                )
            )
            roomTimelineStore.getRelations(EventId("$1other"), RoomId("room", "server"), RelationType.Reference)
                .flatten().first() shouldBe
                    mapOf(
                        EventId("$1event") to
                                TimelineEventRelation(
                                    RoomId("room", "server"),
                                    EventId("$1event"),
                                    RelationType.Reference,
                                    EventId("$1other")
                                )
                    )
        }
    }
    context(TimelineEventHandlerImpl::redactRelation.name) {
        should("delete relation") {
            roomTimelineStore.addRelation(
                TimelineEventRelation(
                    room,
                    EventId("$1event"),
                    RelationType.Reference,
                    EventId("$1other")
                )
            )
            cut.redactRelation(
                MessageEvent(
                    TextMessageEventContent(
                        "hi",
                        relatesTo = RelatesTo.Reference(EventId("$1other"))
                    ),
                    EventId("$1event"),
                    UserId("sender", "server"),
                    room,
                    1234,
                )
            )
            roomTimelineStore.getRelations(EventId("$1other"), room, RelationType.Reference).flattenNotNull().first()
                .shouldNotBeNull().shouldBeEmpty()
        }
    }
})