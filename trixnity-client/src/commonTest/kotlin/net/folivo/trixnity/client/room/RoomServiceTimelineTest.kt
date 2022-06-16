package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.room.RoomService.Companion.LAZY_LOAD_MEMBERS_FILTER
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.BACKWARDS
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class RoomServiceTimelineTest : ShouldSpec({
    timeout = 10_000
    val room = RoomId("room", "server")
    lateinit var store: Store
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncState.RUNNING)

    lateinit var cut: RoomService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = RoomService(
            UserId("alice", "server"),
            store,
            api,
            OlmEventServiceMock(),
            KeyBackupServiceMock(),
            UserServiceMock(),
            MediaServiceMock(),
            currentSyncState,
            MatrixClientConfiguration(),
            scope,
        )
    }

    afterTest {
        scope.cancel()
    }

    suspend fun storeTimeline(vararg events: Event.RoomEvent<*>) = events.map {
        store.roomTimeline.get(it.id, it.roomId)
    }

    context(RoomService::addEventsToTimelineAtEnd.name) {
        val event1 = plainEvent(1)
        val event2 = plainEvent(2)
        val event3 = plainEvent(3)
        context("initial sync") {
            should("add elements to timeline") {
                store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
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
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
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
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
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
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomTimeline.addAll(
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
                store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                    store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                    store.room.update(room) { Room(roomId = room, lastEventId = null) }
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
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
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
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
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
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
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
                    store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                    store.roomTimeline.addAll(
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
                store.room.update(room) { Room(roomId = room, lastEventId = event1.id) }
                store.roomOutboxMessage.update("transactionId1") {
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

                assertSoftly(store.roomTimeline.get(eventId1, room).shouldNotBeNull()) {
                    content shouldBe Result.success(TextMessageEventContent("Hello!"))
                }
                assertSoftly(store.roomTimeline.get(eventId2, room).shouldNotBeNull()) {
                    content shouldBe null
                }
                assertSoftly(store.roomTimeline.get(eventId3, room).shouldNotBeNull()) {
                    content shouldBe null
                }
            }
        }
    }
    context(RoomService::fillTimelineGaps.name) {
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
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
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
                    store.roomTimeline.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                        }
                    })
                    cut.fillTimelineGaps(event3.id, room)
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
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
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
                    store.roomTimeline.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                        }
                    })
                    cut.fillTimelineGaps(event3.id, room)
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
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
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
                    store.roomTimeline.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                            +event4
                            gap("after")
                        }
                    })
                    cut.fillTimelineGaps(event3.id, room)
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
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = BACKWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
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
                    store.roomTimeline.addAll(timeline {
                        fragment {
                            gap("start")
                            +event3
                            +event4
                            gap("after")
                        }
                    })
                    cut.fillTimelineGaps(event3.id, room)
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
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start-3",
                                    "end-1",
                                    dir = BACKWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.fillTimelineGaps(event3.id, room)
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
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start-3",
                                    "end-1",
                                    dir = BACKWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.fillTimelineGaps(event3.id, room)
                        storeTimeline(event1, event2, event3) shouldContainExactly timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                +event2
                                +event3
                            }
                        }
                    }
                }
                context("gap not filled") {
                    should("add element to timeline") {
                        apiConfig.endpoints {
                            matrixJsonEndpoint(
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start-3",
                                    "end-1",
                                    dir = BACKWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
                            fragment {
                                gap("start-1")
                                +event1
                                gap("end-1")
                                gap("start-3")
                                +event3
                            }
                        })
                        cut.fillTimelineGaps(event3.id, room)
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
                            json, mappings,
                            GetEvents(
                                room.e(),
                                "start",
                                dir = FORWARDS,
                                limit = 20,
                                filter = LAZY_LOAD_MEMBERS_FILTER
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
                    store.roomTimeline.addAll(timeline {
                        fragment {
                            +event3
                            gap("start")
                        }
                    })
                    cut.fillTimelineGaps(event3.id, room)
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
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start",
                                    "end",
                                    dir = FORWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
                            fragment {
                                gap("gap-before")
                                +event1
                                +event2
                                gap("start")
                                gap("end")
                                +event5
                            }
                        })
                        cut.fillTimelineGaps(event2.id, room)
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
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start",
                                    "end",
                                    dir = FORWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
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
                        cut.fillTimelineGaps(event3.id, room)
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
                                json, mappings,
                                GetEvents(
                                    room.e(),
                                    "start",
                                    "next",
                                    dir = FORWARDS,
                                    limit = 20,
                                    filter = LAZY_LOAD_MEMBERS_FILTER
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
                        store.roomTimeline.addAll(timeline {
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
                        cut.fillTimelineGaps(event3.id, room)
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
                store.room.update(room) { Room(roomId = room, lastEventId = event3.id, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "start",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                store.roomTimeline.addAll(timeline {
                    fragment {
                        gap("start")
                        +event3
                        gap("next")
                    }
                })
                cut.fillTimelineGaps(event3.id, room)
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
                store.room.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "before-2",
                            "after-1",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                store.roomTimeline.addAll(timeline {
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
                cut.fillTimelineGaps(event2.id, room)
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
                store.room.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "before-3",
                            "after-2",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "after-3",
                            "before-4",
                            dir = FORWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                store.roomTimeline.addAll(timeline {
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
                cut.fillTimelineGaps(event3.id, room)
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
                store.room.update(room) { Room(roomId = room, membership = Membership.JOIN) }
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "after-2",
                            "after-2",
                            dir = BACKWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                        json, mappings,
                        GetEvents(
                            room.e(),
                            "before-4",
                            "before-4",
                            dir = FORWARDS,
                            limit = 20,
                            filter = LAZY_LOAD_MEMBERS_FILTER
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
                store.roomTimeline.addAll(timeline {
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
                cut.fillTimelineGaps(event3.id, room)
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
        should("not allow parallel insertion of events in the same room") {
            val firstEndpointCalled = MutableStateFlow(false)
            val resumeFirstEndpointCall = MutableStateFlow(false)
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    GetEvents(
                        room.e(),
                        "before-3",
                        dir = BACKWARDS,
                        limit = 20,
                        filter = LAZY_LOAD_MEMBERS_FILTER
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
                    json, mappings,
                    GetEvents(
                        room.e(),
                        "before-3",
                        dir = BACKWARDS,
                        limit = 20,
                        filter = LAZY_LOAD_MEMBERS_FILTER
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
            store.roomTimeline.addAll(timeline {
                fragment {
                    gap("before-3")
                    +event3
                }
            })
            launch {
                cut.fillTimelineGaps(event3.id, room)
            }
            firstEndpointCalled.first { it }
            val otherJob = launch(start = CoroutineStart.UNDISPATCHED) {
                cut.fillTimelineGaps(event3.id, room)
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
})