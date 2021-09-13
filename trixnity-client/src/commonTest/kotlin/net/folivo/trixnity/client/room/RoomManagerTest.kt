package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.RedactedRoomEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.UnsignedData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

@OptIn(ExperimentalKotest::class)
class RoomManagerTest : ShouldSpec({
    val scope = CoroutineScope(Dispatchers.Default)
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val room = MatrixId.RoomId("room", "server")
    val store = InMemoryStore()
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmManager>()
    val cut = RoomManager(store, api, olm, LoggerFactory.default)

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
    }

    afterTest {
        clearMocks(api, olm)
        store.clear()
    }

    fun textEvent(i: Long = 24): RoomEvent<TextMessageEventContent> {
        return RoomEvent(
            TextMessageEventContent("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i
        )
    }

    fun nameEvent(i: Long = 60): Event.StateEvent<NameEventContent> {
        return Event.StateEvent(
            NameEventContent("The room name"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            room,
            i,
            stateKey = ""
        )
    }

    context(RoomManager::setLastEventAt.name) {
        should("set last event from room event") {
            cut.setLastEventAt(textEvent(24))
            store.rooms.byId(room).value?.lastEventAt shouldBe Instant.fromEpochMilliseconds(24)
        }
        should("set last event from state event") {
            cut.setLastEventAt(
                Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.lastEventAt shouldBe Instant.fromEpochMilliseconds(25)
        }
    }

    context(RoomManager::redactTimelineEvent.name) {

        context("with existent event") {
            should("redact room event") {
                val event1 = textEvent(1)
                val event2 = textEvent(2)
                val event3 = textEvent(3)
                store.rooms.timeline.updateAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            decryptedEvent = null,
                            decryptionException = null,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            decryptedEvent = MegolmEvent(TextMessageEventContent("hi"), room),
                            decryptionException = DecryptionException.ValidationFailed,
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            decryptedEvent = null,
                            decryptionException = null,
                            roomId = room,
                            eventId = event3.id,
                            previousEventId = event3.id,
                            nextEventId = null,
                            gap = null
                        )
                    )
                )
                val redactionEvent = RoomEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = event2.id),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                    event shouldBe RoomEvent(
                        RedactedRoomEventContent("m.room.message"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedData(
                            redactedBecause = redactionEvent
                        )
                    )
                    decryptedEvent shouldBe null
                    decryptionException shouldBe null
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
                store.rooms.timeline.updateAll(
                    listOf(
                        TimelineEvent(
                            event = event1,
                            decryptedEvent = null,
                            decryptionException = null,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            decryptedEvent = null,
                            decryptionException = DecryptionException.ValidationFailed,
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            decryptedEvent = null,
                            decryptionException = null,
                            roomId = room,
                            eventId = event3.id,
                            previousEventId = event3.id,
                            nextEventId = null,
                            gap = null
                        )
                    )
                )
                val redactionEvent = RoomEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = event2.id),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                    event shouldBe Event.StateEvent(
                        RedactedStateEventContent("m.room.name"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedData(
                            redactedBecause = redactionEvent
                        ),
                        ""
                    )
                    decryptedEvent shouldBe null
                    decryptionException shouldBe null
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
                    decryptedEvent = null,
                    decryptionException = null,
                    roomId = room,
                    eventId = event1.id,
                    previousEventId = null,
                    nextEventId = event2.id,
                    gap = null
                )
                val timelineEvent2 = TimelineEvent(
                    event = event2,
                    decryptedEvent = null,
                    decryptionException = DecryptionException.ValidationFailed,
                    roomId = room,
                    eventId = event2.id,
                    previousEventId = event1.id,
                    nextEventId = null,
                    gap = null
                )
                store.rooms.timeline.updateAll(
                    listOf(
                        timelineEvent1,
                        timelineEvent2,
                    )
                )

                val redactionEvent = RoomEvent(
                    content = RedactionEventContent(reason = "Spamming", redacts = EventId("\$incorrectlyEvent")),
                    id = EventId("\$redact"),
                    sender = alice,
                    roomId = room,
                    originTimestamp = 3
                )
                cut.redactTimelineEvent(redactionEvent)
                store.rooms.timeline.byId(EventId("\$incorrectlyEvent"), room).value shouldBe null
                store.rooms.timeline.byId(timelineEvent1.eventId, room).value shouldBe timelineEvent1
                store.rooms.timeline.byId(timelineEvent2.eventId, room).value shouldBe timelineEvent2
            }
        }
    }

    context(RoomManager::setEncryptionAlgorithm.name) {
        should("update set encryption algorithm") {
            cut.setEncryptionAlgorithm(
                Event.StateEvent(
                    EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.encryptionAlgorithm shouldBe EncryptionAlgorithm.Megolm
        }
    }

    context(RoomManager::setOwnMembership.name) {
        should("set own membership of a room") {
            store.account.userId.value = alice
            cut.setOwnMembership(
                Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.LEAVE),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.ownMembership shouldBe MemberEventContent.Membership.LEAVE
        }
    }
    context(RoomManager::setUnreadMessageCount.name) {
        should("set unread message count for room") {
            store.rooms.update(room) { simpleRoom.copy(roomId = room) }
            cut.setUnreadMessageCount(room, 24)
            store.rooms.byId(room).value?.unreadMessageCount shouldBe 24
        }
    }
    context(RoomManager::loadMembers.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = room, membersLoaded = true)
            store.rooms.update(room) { storedRoom }
            cut.loadMembers(room)
            store.rooms.byId(room).value shouldBe storedRoom
        }
        should("load members") {
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns flowOf(
                Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                ),
                Event.StateEvent(
                    MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                    EventId("\$event2"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
            )
            val storedRoom = simpleRoom.copy(roomId = room, membersLoaded = false)
            store.rooms.update(room) { storedRoom }
            cut.loadMembers(room)
            store.rooms.byId(room).value?.membersLoaded shouldBe true
            store.rooms.state.byId<MemberEventContent>(
                room,
                alice.full
            ).value?.content?.membership shouldBe MemberEventContent.Membership.JOIN
            store.rooms.state.byId<MemberEventContent>(
                room,
                bob.full
            ).value?.content?.membership shouldBe MemberEventContent.Membership.JOIN
            store.deviceKeys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
        }
    }
    context(RoomManager::getTimelineEvent.name) {
        val eventId = EventId("\$event1")
        val session = "SESSION"
        val senderKey = Key.Curve25519Key(null, "senderKey")
        val encryptedEventContent = MegolmEncryptedEventContent(
            "ciphertext", senderKey, "SENDER", session
        )
        val encryptedTimelineEvent = TimelineEvent(
            event = RoomEvent(
                encryptedEventContent,
                EventId("\$event1"),
                UserId("sender", "server"),
                room,
                1
            ),
            roomId = room,
            eventId = eventId,
            previousEventId = null,
            nextEventId = null,
            gap = null
        )
        context("should just return event") {
            withData(
                mapOf(
                    "with already encrypted event" to encryptedTimelineEvent.copy(
                        decryptedEvent = MegolmEvent(TextMessageEventContent("hi"), room)
                    ),
                    "with encryption error" to encryptedTimelineEvent.copy(
                        decryptionException = DecryptionException.ValidationFailed
                    ),
                    "without RoomEvent" to encryptedTimelineEvent.copy(
                        event = nameEvent(24)
                    ),
                    "without MegolmEncryptedEventContent" to encryptedTimelineEvent.copy(
                        event = textEvent(48)
                    )
                )
            ) { timelineEvent ->
                store.rooms.timeline.updateAll(listOf(timelineEvent))
                cut.getTimelineEvent(eventId, room).value shouldBe timelineEvent

                // event gets changed later (e.g. redaction)
                store.rooms.timeline.updateAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(eventId, room)
                delay(20)
                store.rooms.timeline.updateAll(listOf(timelineEvent))
                store.olm.inboundMegolmSession(room, session, senderKey).update {
                    StoredOlmInboundMegolmSession(session, senderKey, room, "pickle")
                }
                delay(20)
                result.value shouldBe timelineEvent
            }
        }
        context("event can be decrypted") {
            should("decrypt event") {
                val decryptedEvent = MegolmEvent(TextMessageEventContent("decrypted"), room)
                coEvery { olm.events.decryptMegolm(any()) } returns decryptedEvent
                store.rooms.timeline.updateAll(listOf(encryptedTimelineEvent))
                store.olm.inboundMegolmSession(room, session, senderKey).update {
                    StoredOlmInboundMegolmSession(session, senderKey, room, "pickle")
                }
                val result = cut.getTimelineEvent(eventId, room).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    this.decryptedEvent shouldBe decryptedEvent
                    decryptionException shouldBe null
                }
            }
            should("handle error") {
                coEvery { olm.events.decryptMegolm(any()) } throws DecryptionException.ValidationFailed
                store.rooms.timeline.updateAll(listOf(encryptedTimelineEvent))
                store.olm.inboundMegolmSession(room, session, senderKey).update {
                    StoredOlmInboundMegolmSession(session, senderKey, room, "pickle")
                }
                val result = cut.getTimelineEvent(eventId, room).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    decryptedEvent shouldBe null
                    decryptionException shouldBe DecryptionException.ValidationFailed
                }
            }
            should("wait for olm session") {
                val decryptedEvent = MegolmEvent(TextMessageEventContent("decrypted"), room)
                coEvery { olm.events.decryptMegolm(any()) } returns decryptedEvent
                store.rooms.timeline.updateAll(listOf(encryptedTimelineEvent))

                val result = cut.getTimelineEvent(eventId, room)
                delay(20)
                store.olm.inboundMegolmSession(room, session, senderKey).update {
                    StoredOlmInboundMegolmSession(session, senderKey, room, "pickle")
                }
                delay(20)
                assertSoftly(result.value) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    this.decryptedEvent shouldBe decryptedEvent
                    decryptionException shouldBe null
                }
            }
        }
    }
    context(RoomManager::getLastTimelineEvent.name) {
        should("return last event of room") {
            val initialRoom = Room(room, fromEpochMilliseconds(24), null)
            val event1 = textEvent(1)
            val event2 = textEvent(2)
            val event2Timeline = TimelineEvent(
                event = event2,
                decryptedEvent = null,
                decryptionException = null,
                roomId = room,
                eventId = event2.id,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            store.rooms.timeline.updateAll(listOf(event2Timeline))
            val result = scope.async {
                cut.getLastTimelineEvent(room).take(3).toList()
            }
            store.rooms.update(room) { initialRoom }
            delay(20)
            store.rooms.update(room) { initialRoom.copy(lastEventId = event1.id) }
            delay(20)
            store.rooms.update(room) { initialRoom.copy(lastEventId = event2.id) }
            assertSoftly(result.await()) {
                this[0] shouldBe null
                this[1] shouldNotBe null
                this[1]?.value shouldBe null
                this[2]?.value shouldBe event2Timeline
            }
        }
    }
})