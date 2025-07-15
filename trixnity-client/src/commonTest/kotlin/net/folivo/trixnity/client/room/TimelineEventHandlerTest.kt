package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.TimelineEventContentError
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
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

class TimelineEventHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val room = RoomId("!room:server")
    private val event1 = plainEvent(1)
    private val event2 = plainEvent(2)
    private val event3 = plainEvent(3)
    private val event4 = plainEvent(4)
    private val event5 = plainEvent(5)

    private val accountStore = getInMemoryAccountStore().apply {
        scheduleSetup { updateAccount { it?.copy(filterId = "1") } }
    }
    private val roomStore = getInMemoryRoomStore()
    private val roomTimelineStore = getInMemoryRoomTimelineStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(config = apiConfig)

    private val filter ="""
        {"room":{"state":{"types":[]},"timeline":{"types":["m.room.message","m.reaction","m.room.redaction","m.room.encrypted","m.key.verification.start","m.key.verification.ready","m.key.verification.done","m.key.verification.cancel","m.key.verification.accept","m.key.verification.key","m.key.verification.mac","m.call.invite","m.call.candidates","m.call.answer","m.call.hangup","m.call.negotiate","m.call.reject","m.call.select_answer","m.call.sdp_stream_metadata_changed","m.room.avatar","m.room.canonical_alias","m.room.create","m.room.join_rules","m.room.member","m.room.name","m.room.pinned_events","m.room.power_levels","m.room.topic","m.room.encryption","m.room.history_visibility","m.room.third_party_invite","m.room.guest_access","m.room.server_acl","m.room.tombstone","m.policy.rule.user","m.policy.rule.room","m.policy.rule.server","m.space.parent","m.space.child"]}}}
    """.trimIndent()

    private val cut = TimelineEventHandlerImpl(
        api,
        roomStore,
        roomTimelineStore,
        createMatrixEventJson(),
        createDefaultEventContentSerializerMappings(),
        MatrixClientConfiguration(),
        TransactionManagerMock(),
    )


    private suspend fun storeTimeline(vararg events: RoomEvent<*>) = events.map {
        roomTimelineStore.get(it.id, it.roomId).first()
    }

    private fun nameEvent(i: Long = 60): StateEvent<NameEventContent> {
        return StateEvent(
            NameEventContent("The room name"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i,
            stateKey = ""
        )
    }

    private fun textEvent(i: Long = 24): MessageEvent<RoomMessageEventContent.TextBased.Text> {
        return MessageEvent(
            RoomMessageEventContent.TextBased.Text("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }

    @Test
    fun `handleRedactions » with existent event » redact room event`() = runTest {
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event3 = textEvent(3)
        roomTimelineStore.addAll(
            listOf(
                TimelineEvent(
                    event = event1,
                    content = null,
                    previousEventId = null,
                    nextEventId = event2.id,
                    gap = null
                ),
                TimelineEvent(
                    event = event2,
                    content = Result.failure(TimelineEventContentError.DecryptionTimeout),
                    previousEventId = event1.id,
                    nextEventId = event3.id,
                    gap = null
                ),
                TimelineEvent(
                    event = event3,
                    content = null,
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
        with(cut) {
            listOf(redactionEvent).handleRedactions()
        }
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

    @Test
    fun `handleRedactions » with existent event » not redact room event twice`() = runTest {
        val messageEventId = EventId("\$message")
        val redactionEventId = EventId("\$redact")

        val redactionEvent = MessageEvent(
            content = RedactionEventContent(redacts = messageEventId),
            id = redactionEventId,
            sender = alice,
            roomId = room,
            originTimestamp = 2
        )

        val messageEvent = MessageEvent(
            content = RedactedEventContent("m.room.message"),
            id = messageEventId,
            sender = alice,
            roomId = room,
            originTimestamp = 1,
            UnsignedRoomEventData.UnsignedMessageEventData(
                redactedBecause = redactionEvent
            )
        )

        with(cut) {
            val events = listOf(
                messageEvent,
                redactionEvent
            ).handleRedactions()
            roomTimelineStore.addEventsToTimeline(
                startEvent = TimelineEvent(
                    event = events.first(),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null
                ),
                roomId = room,
                previousToken = null,
                previousHasGap = true,
                previousEvent = null,
                previousEventChunk = null,
                nextToken = "token",
                nextHasGap = true,
                nextEvent = null,
                nextEventChunk = events.drop(1),
            )
        }
        assertSoftly(roomTimelineStore.get(messageEvent.id, room).first().shouldNotBeNull()) {
            event shouldBe MessageEvent(
                content = RedactedEventContent("m.room.message"),
                id = EventId("\$message"),
                sender = alice,
                roomId = room,
                originTimestamp = 1,
                UnsignedRoomEventData.UnsignedMessageEventData(
                    redactedBecause = redactionEvent
                )
            )
            content shouldBe Result.success(RedactedEventContent("m.room.message"))
            roomId shouldBe room
            eventId shouldBe messageEvent.id
        }
    }

    @Test
    fun `handleRedactions » with existent event » redact state event`() = runTest {
        val event1 = nameEvent(1)
        val event2 = nameEvent(2)
        val event3 = nameEvent(3)
        roomTimelineStore.addAll(
            listOf(
                TimelineEvent(
                    event = event1,
                    content = null,
                    previousEventId = null,
                    nextEventId = event2.id,
                    gap = null
                ),
                TimelineEvent(
                    event = event2,
                    content = Result.failure(TimelineEventContentError.DecryptionTimeout),
                    previousEventId = event1.id,
                    nextEventId = event3.id,
                    gap = null
                ),
                TimelineEvent(
                    event = event3,
                    content = null,
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
        with(cut) {
            listOf(redactionEvent).handleRedactions()
        }
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

    @Test
    fun `handleRedactions » with nonexistent event » do nothing`() = runTest {
        val event1 = nameEvent(1)
        val event2 = nameEvent(2)
        val timelineEvent1 = TimelineEvent(
            event = event1,
            content = null,
            previousEventId = null,
            nextEventId = event2.id,
            gap = null
        )
        val timelineEvent2 = TimelineEvent(
            event = event2,
            content = Result.failure(TimelineEventContentError.DecryptionTimeout),
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
        with(cut) {
            listOf(redactionEvent).handleRedactions()
        }
        roomTimelineStore.get(EventId("\$incorrectlyEvent"), room).first() shouldBe null
        roomTimelineStore.get(timelineEvent1.eventId, room).first() shouldBe timelineEvent1
        roomTimelineStore.get(timelineEvent2.eventId, room).first() shouldBe timelineEvent2
    }

    @Test
    fun `addEventsToTimelineAtEnd » initial sync » add elements to timeline`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » initial sync » add elements to timeline with gap`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » initial sync » add one element to timeline`() = runTest {
        roomStore.update(room) { Room(roomId = room, lastEventId = null) }
        cut.addEventsToTimelineAtEnd(room, listOf(event1), null, "next", false)
        storeTimeline(event1) shouldContainExactly timeline {
            fragment {
                +event1
                gap("next")
            }
        }
    }

    @Test
    fun `addEventsToTimelineAtEnd » without gap » add elements to timeline`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » without gap » add elements to gappy timeline`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » without gap » add one element to timeline`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » without gap » add one element to timeline that already exists`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » without gap » filter duplicate events`() = runTest {
        roomStore.update(room) { Room(roomId = room, lastEventId = null) }
        cut.addEventsToTimelineAtEnd(room, listOf(event1, event1), "previous", "next", false)
        storeTimeline(event1) shouldContainExactly timeline {
            fragment {
                +event1
                gap("next")
            }
        }
    }

    @Test
    fun `addEventsToTimelineAtEnd » with gap » without previous events » add elements to timeline`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » with gap » without previous events » add one element to timeline`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » with gap » with previous events » add elements to gappy timeline`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » with gap » with previous events » add elements to timeline`() = runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » with gap » with previous events » add elements to timeline with existing gap`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » with gap » with previous events » add one element to timeline`() =
        runTest {
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

    @Test
    fun `addEventsToTimelineAtEnd » process new redactions`() = runTest {
        val redactionEvent1 = MessageEvent(
            RedactionEventContent(redacts = EventId("\$event1")),
            EventId("\$event3"),
            UserId("sender", "server"),
            RoomId("!room:server"),
            3
        )
        val redactionEvent2 = MessageEvent(
            RedactionEventContent(redacts = EventId("\$event2")),
            EventId("\$event4"),
            UserId("sender", "server"),
            RoomId("!room:server"),
            4
        )
        roomStore.update(room) { Room(roomId = room, lastEventId = event1.id) }
        roomTimelineStore.addAll(
            timeline {
                fragment {
                    +event1
                    gap("oldPrevious")
                }
            }
        )
        cut.addEventsToTimelineAtEnd(
            room,
            listOf(event2, redactionEvent1, redactionEvent2),
            "previous",
            "next",
            true
        )
        storeTimeline(event1, event2, redactionEvent1, redactionEvent2) shouldContainExactly timeline {
            fragment {
                +event1.redacted(because = redactionEvent1)
                gap("oldPrevious")
                gap("previous")
                +event2.redacted(because = redactionEvent2)
                +redactionEvent1
                +redactionEvent2
                gap("next")
            }
        }
    }

    @Test
    fun `handleSyncResponse » lastEventId » set lastEventId from room event`() = runTest {
        roomStore.update(room) { simpleRoom.copy(roomId = room) }
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

    @Test
    fun `handleSyncResponse » lastEventId » set lastEventId from state event`() = runTest {
        roomStore.update(room) { simpleRoom.copy(roomId = room) }
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » add elements to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » add one element to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » detect start of timeline when start and end are the same`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » detect start of timeline when end is null`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » gap filled » add element to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start-3",
                        "end-1",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » gap filled » add element to timeline when end is null`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start-3",
                        "end-1",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » gap filled » ignore overlapping events`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start-3",
                        "end-1",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » gap filled » prevent loop`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "gap",
                        null,
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has previous gap » gap not filled » add element to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start-3",
                        "end-1",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has next gap » add elements to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.FORWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has next gap » gap filled » add elements to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        "end",
                        dir = GetEvents.Direction.FORWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has next gap » gap filled » add elements to timeline when end is null`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        "end",
                        dir = GetEvents.Direction.FORWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has next gap » gap filled » ignore overlapping events`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        "end",
                        dir = GetEvents.Direction.FORWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » start event has next gap » gap not filled » add element to timeline`() =
        runTest {
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        "next",
                        dir = GetEvents.Direction.FORWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » only fetch event before when last event of room`() =
        runTest {
            roomStore.update(room) { Room(roomId = room, lastEventId = event3.id, membership = Membership.JOIN) }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "start",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » should detect loop due to event found in chunk`() =
        runTest {
            roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "before-2",
                        "after-1",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » should handle gap filling without new events`() =
        runTest {
            roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "before-3",
                        "after-2",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » start event does exist in store » should handle gap filling without new events and same tokens`() =
        runTest {
            roomStore.update(room) { Room(roomId = room, membership = Membership.JOIN) }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    GetEvents(
                        room,
                        "after-2",
                        "after-2",
                        dir = GetEvents.Direction.BACKWARDS,
                        limit = 20,
                        filter = filter,
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
                        filter = filter,
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

    @Test
    fun `unsafeFillTimelineGaps » process redactions from batch`() = runTest {
        val redactionEvent1 = MessageEvent(
            RedactionEventContent(redacts = EventId("\$event1")),
            EventId("\$event3"),
            UserId("sender", "server"),
            RoomId("!room:server"),
            3
        )
        val redactionEvent2 = MessageEvent(
            RedactionEventContent(redacts = EventId("\$event2")),
            EventId("\$event4"),
            UserId("sender", "server"),
            RoomId("!room:server"),
            4
        )
        apiConfig.endpoints {
            matrixJsonEndpoint(
                GetEvents(
                    room,
                    "start",
                    dir = GetEvents.Direction.BACKWARDS,
                    limit = 20,
                    filter = filter,
                )
            ) {
                GetEvents.Response(
                    start = "start",
                    end = "end",
                    chunk = listOf(redactionEvent2, redactionEvent1, event2),
                    state = listOf()
                )
            }
        }
        roomTimelineStore.addAll(timeline {
            fragment {
                +event1
                gap("end")
                gap("start")
                +event5
            }
        })
        cut.unsafeFillTimelineGaps(event5.id, room)
        storeTimeline(
            event1,
            event2,
            redactionEvent1,
            redactionEvent2,
            event5
        ) shouldContainExactlyInAnyOrder timeline {
            fragment {
                +event1.redacted(because = redactionEvent1)
                +event2.redacted(because = redactionEvent2)
                +redactionEvent1
                +redactionEvent2
                +event5
            }
        }
    }

    @Test
    fun `unsafeFillTimelineGaps » not allow parallel insertion of events in the same room`() = runTest {
        val firstEndpointCalled = MutableStateFlow(false)
        val resumeFirstEndpointCall = MutableStateFlow(false)
        apiConfig.endpoints {
            matrixJsonEndpoint(
                GetEvents(
                    room,
                    "before-3",
                    dir = GetEvents.Direction.BACKWARDS,
                    limit = 20,
                    filter = filter,
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
                    filter = filter,
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

    @Test
    fun `addRelation » add relation`() = runTest {
        cut.addRelation(
            MessageEvent(
                RoomMessageEventContent.TextBased.Text(
                    "hi",
                    relatesTo = RelatesTo.Reference(EventId("$1other"))
                ),
                EventId("$1event"),
                UserId("sender", "server"),
                RoomId("!room:server"),
                1234,
            )
        )
        roomTimelineStore.getRelations(EventId("$1other"), RoomId("!room:server"), RelationType.Reference)
            .flatten().first() shouldBe
                mapOf(
                    EventId("$1event") to
                            TimelineEventRelation(
                                RoomId("!room:server"),
                                EventId("$1event"),
                                RelationType.Reference,
                                EventId("$1other")
                            )
                )
    }

    @Test
    fun `redactRelation » delete relation`() = runTest {
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
                RoomMessageEventContent.TextBased.Text(
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

    @Test
    fun `redactRelation » delete replace relations`() = runTest {
        roomTimelineStore.addRelation(
            TimelineEventRelation(
                room,
                EventId("$1other1"),
                RelationType.Replace,
                EventId("$1event")
            )
        )
        roomTimelineStore.addRelation(
            TimelineEventRelation(
                room,
                EventId("$1other2"),
                RelationType.Replace,
                EventId("$1event")
            )
        )
        cut.redactRelation(
            MessageEvent(
                RoomMessageEventContent.TextBased.Text("hi"),
                EventId("$1event"),
                UserId("sender", "server"),
                room,
                1234,
            )
        )
        roomTimelineStore.getRelations(EventId("$1event"), room, RelationType.Replace).flattenNotNull().first()
            .shouldNotBeNull().shouldBeEmpty()
    }
}

private fun MessageEvent<*>.redacted(because: MessageEvent<RedactionEventContent>) =
    MessageEvent(
        content = RedactedEventContent("m.room.message"),
        id = id,
        sender = sender,
        roomId = roomId,
        originTimestamp = originTimestamp,
        unsigned = UnsignedRoomEventData.UnsignedMessageEventData(redactedBecause = because)
    )