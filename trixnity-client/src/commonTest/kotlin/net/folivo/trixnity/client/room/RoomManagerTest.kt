package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.media.FileTransferProgress
import net.folivo.trixnity.client.api.sync.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.client.api.sync.SyncApiClient.SyncState.STARTED
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.media.MediaManager
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.MatrixId.EventId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MegolmEvent
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

@OptIn(ExperimentalKotest::class)
class RoomManagerTest : ShouldSpec({
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val room = simpleRoom.roomId
    val store = InMemoryStore()
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmManager>()
    val media = mockk<MediaManager>()
    val cut = RoomManager(store, api, olm, media, loggerFactory = LoggerFactory.default)

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
    }

    afterTest {
        clearMocks(api, olm)
        store.clear()
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
            store.rooms.byId(room).value?.lastEventAt shouldBe fromEpochMilliseconds(24)
        }
        should("set last event from state event") {
            cut.setLastEventAt(
                Event.StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.lastEventAt shouldBe fromEpochMilliseconds(25)
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
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            decryptedEvent = Result.failure(DecryptionException.ValidationFailed),
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            decryptedEvent = null,
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
                assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                    event shouldBe MessageEvent(
                        RedactedMessageEventContent("m.room.message"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedMessageEventData(
                            redactedBecause = redactionEvent
                        )
                    )
                    decryptedEvent shouldBe null
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
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            decryptedEvent = Result.failure(DecryptionException.ValidationFailed),
                            roomId = room,
                            eventId = event2.id,
                            previousEventId = event1.id,
                            nextEventId = event3.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event3,
                            decryptedEvent = null,
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
                assertSoftly(store.rooms.timeline.byId(event2.id, room).value!!) {
                    event shouldBe Event.StateEvent(
                        RedactedStateEventContent("m.room.name"),
                        event2.id,
                        UserId("sender", "server"),
                        room,
                        2,
                        UnsignedStateEventData(
                            redactedBecause = redactionEvent
                        ),
                        ""
                    )
                    decryptedEvent shouldBe null
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
                    roomId = room,
                    eventId = event1.id,
                    previousEventId = null,
                    nextEventId = event2.id,
                    gap = null
                )
                val timelineEvent2 = TimelineEvent(
                    event = event2,
                    decryptedEvent = Result.failure(DecryptionException.ValidationFailed),
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

                val redactionEvent = MessageEvent(
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
                    EncryptionEventContent(algorithm = Megolm),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.encryptionAlgorithm shouldBe Megolm
        }
    }

    context(RoomManager::setOwnMembership.name) {
        should("set own membership of a room") {
            store.account.userId.value = alice
            cut.setOwnMembership(
                Event.StateEvent(
                    MemberEventContent(membership = LEAVE),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.rooms.byId(room).value?.ownMembership shouldBe LEAVE
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
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                ),
                Event.StateEvent(
                    MemberEventContent(membership = JOIN),
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
            ).value?.content?.membership shouldBe JOIN
            store.rooms.state.byId<MemberEventContent>(
                room,
                bob.full
            ).value?.content?.membership shouldBe JOIN
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
            event = MessageEvent(
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
                        decryptedEvent = Result.success(MegolmEvent(TextMessageEventContent("hi"), room))
                    ),
                    "with encryption error" to encryptedTimelineEvent.copy(
                        decryptedEvent = Result.failure(DecryptionException.ValidationFailed)
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
                val expectedDecryptedEvent = MegolmEvent(TextMessageEventContent("decrypted"), room)
                coEvery { olm.events.decryptMegolm(any()) } returns expectedDecryptedEvent
                store.rooms.timeline.updateAll(listOf(encryptedTimelineEvent))
                store.olm.inboundMegolmSession(room, session, senderKey).update {
                    StoredOlmInboundMegolmSession(session, senderKey, room, "pickle")
                }
                val result = cut.getTimelineEvent(eventId, room).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    decryptedEvent?.getOrNull() shouldBe expectedDecryptedEvent
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
                    decryptedEvent?.exceptionOrNull() shouldBe DecryptionException.ValidationFailed
                }
            }
            should("wait for olm session") {
                val expectedDecryptedEvent = MegolmEvent(TextMessageEventContent("decrypted"), room)
                coEvery { olm.events.decryptMegolm(any()) } returns expectedDecryptedEvent
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
                    decryptedEvent?.getOrNull() shouldBe expectedDecryptedEvent
                }
            }
        }
    }
    context(RoomManager::getLastTimelineEvent.name) {
        should("return last event of room") {
            val initialRoom = Room(room, lastEventAt = fromEpochMilliseconds(24), lastEventId = null)
            val event1 = textEvent(1)
            val event2 = textEvent(2)
            val event2Timeline = TimelineEvent(
                event = event2,
                decryptedEvent = null,
                roomId = room,
                eventId = event2.id,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            store.rooms.timeline.updateAll(listOf(event2Timeline))
            val result = async {
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
    context(RoomManager::sendMessage.name) {
        should("just save message in store for later use") {
            val content = TextMessageEventContent("hi")
            cut.sendMessage(content, room)
            val outboundMessages = store.rooms.outboxMessages.all().value
            outboundMessages shouldHaveSize 1
            assertSoftly(outboundMessages.first()) {
                roomId shouldBe room
                content shouldBe content
                transactionId.length shouldBeGreaterThan 12
            }
        }
    }
    context(RoomManager::syncOutboxMessage.name) {
        should("ignore messages from foreign users") {
            store.account.userId.value = UserId("me", "server")
            val roomOutboxMessage = RoomOutboxMessage(TextMessageEventContent("hi"), room, "transaction", true)
            store.rooms.outboxMessages.add(roomOutboxMessage)
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                UserId("sender", "server"),
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            store.rooms.outboxMessages.all().value.first() shouldBe roomOutboxMessage
        }
        should("remove outbox message from us") {
            store.account.userId.value = UserId("me", "server")
            val roomOutboxMessage = RoomOutboxMessage(TextMessageEventContent("hi"), room, "transaction", true)
            store.rooms.outboxMessages.add(roomOutboxMessage)
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                UserId("me", "server"),
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            store.rooms.outboxMessages.all().value.size shouldBe 0
        }
    }
    context(RoomManager::processOutboxMessages.name) {
        should("wait until connected, upload media, send message and mark outbox message as sent") {
            store.rooms.update(room) { simpleRoom }
            val mxcUrl = "mxc://dino"
            val cacheUrl = "cache://unicorn"
            val mediaUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
            val message1 =
                RoomOutboxMessage(
                    ImageMessageEventContent("hi.png", url = cacheUrl),
                    room, "transaction1", false, mediaUploadProgress
                )
            val message2 = RoomOutboxMessage(TextMessageEventContent("hi"), room, "transaction2", false)
            store.rooms.outboxMessages.add(message1)
            store.rooms.outboxMessages.add(message2)
            coEvery { media.uploadMedia(any(), any()) } returns mxcUrl
            coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("event", "server")
            val syncState = MutableStateFlow(STARTED)
            coEvery { api.sync.currentSyncState } returns syncState.asStateFlow()

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.rooms.outboxMessages.all()) }
            delay(50)
            job.isActive shouldBe true
            syncState.value = RUNNING

            coVerify(timeout = 500) {
                media.uploadMedia(cacheUrl, mediaUploadProgress)
                api.rooms.sendMessageEvent(room, ImageMessageEventContent("hi.png", url = mxcUrl), "transaction1")
                api.rooms.sendMessageEvent(room, TextMessageEventContent("hi"), "transaction2")
            }
            val outboxMessages = store.rooms.outboxMessages.all().value
            outboxMessages shouldHaveSize 2
            outboxMessages[0].wasSent shouldBe true
            outboxMessages[1].wasSent shouldBe true
            job.cancel()
        }
        should("encrypt events in encrypted rooms") {
            store.rooms.update(room) { simpleRoom.copy(encryptionAlgorithm = Megolm) }
            val message = RoomOutboxMessage(TextMessageEventContent("hi"), room, "transaction", false)
            store.rooms.outboxMessages.add(message)
            val encryptionState =
                Event.StateEvent(
                    EncryptionEventContent(),
                    EventId("\$stateEvent"),
                    UserId("sender", "server"),
                    room,
                    1234,
                    stateKey = ""
                )
            store.rooms.state.update(encryptionState)
            coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("event", "server")
            val megolmEventContent = mockk<MegolmEncryptedEventContent>()
            coEvery { olm.events.encryptMegolm(any(), any(), any()) } returns megolmEventContent
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns flowOf()
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING).asStateFlow()

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.rooms.outboxMessages.all()) }

            coVerify(timeout = 500) {
                api.rooms.sendMessageEvent(room, megolmEventContent, "transaction")
                olm.events.encryptMegolm(TextMessageEventContent("hi"), room, EncryptionEventContent())
                api.rooms.getMembers(any(), any(), any(), any(), any())
            }
            val outboxMessages = store.rooms.outboxMessages.all().value
            outboxMessages shouldHaveSize 1
            outboxMessages[0].wasSent shouldBe true
            job.cancel()
        }
        should("retry on sending error") {
            store.rooms.update(room) { simpleRoom }
            val message = RoomOutboxMessage(TextMessageEventContent("hi"), room, "transaction", false)
            store.rooms.outboxMessages.add(message)
            coEvery {
                api.rooms.sendMessageEvent(any(), any(), any(), any())
            } throws IllegalArgumentException("wtf") andThen EventId("event", "server")
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING).asStateFlow()

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.rooms.outboxMessages.all()) }

            coVerify(exactly = 2, timeout = 500) {
                api.rooms.sendMessageEvent(room, TextMessageEventContent("hi"), "transaction")
            }
            val outboxMessages = store.rooms.outboxMessages.all().value
            outboxMessages shouldHaveSize 1
            outboxMessages[0].wasSent shouldBe true
            job.cancel()
        }
    }
})