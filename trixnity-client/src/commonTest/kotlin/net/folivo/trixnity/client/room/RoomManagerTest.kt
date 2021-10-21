package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
import net.folivo.trixnity.client.testutils.createInMemoryStore
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RedactedMessageEventContent
import net.folivo.trixnity.core.model.events.RedactedStateEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKotest::class, kotlin.time.ExperimentalTime::class)
class RoomManagerTest : ShouldSpec({
    timeout = 2000
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val room = simpleRoom.roomId
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    val api = mockk<MatrixApiClient>()
    val olm = mockk<OlmManager>()
    val media = mockk<MediaManager>()
    lateinit var cut: RoomManager

    beforeTest {
        every { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
        storeScope = CoroutineScope(Dispatchers.Default)
        store = createInMemoryStore(storeScope).apply { init() }
        cut = RoomManager(store, api, olm, media, loggerFactory = LoggerFactory.default)
    }

    afterTest {
        clearAllMocks()
        storeScope.cancel()
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

    context(RoomManager::setLastEventAt.name) {
        should("set last event from room event") {
            cut.setLastEventAt(textEvent(24))
            store.room.get(room).value?.lastEventAt shouldBe fromEpochMilliseconds(24)
        }
        should("set last event from state event") {
            cut.setLastEventAt(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.room.get(room).value?.lastEventAt shouldBe fromEpochMilliseconds(25)
        }
    }

    context(RoomManager::redactTimelineEvent.name) {
        context("with existent event") {
            should("redact room event") {
                val event1 = textEvent(1)
                val event2 = textEvent(2)
                val event3 = textEvent(3)
                store.roomTimeline.addAll(
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
                assertSoftly(store.roomTimeline.get(event2.id, room).value!!) {
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
                store.roomTimeline.addAll(
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
                assertSoftly(store.roomTimeline.get(event2.id, room).value!!) {
                    event shouldBe StateEvent(
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
                store.roomTimeline.addAll(
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
                store.roomTimeline.get(EventId("\$incorrectlyEvent"), room).value shouldBe null
                store.roomTimeline.get(timelineEvent1.eventId, room).value shouldBe timelineEvent1
                store.roomTimeline.get(timelineEvent2.eventId, room).value shouldBe timelineEvent2
            }
        }
    }

    context(RoomManager::setEncryptionAlgorithm.name) {
        should("update set encryption algorithm") {
            cut.setEncryptionAlgorithm(
                StateEvent(
                    EncryptionEventContent(algorithm = Megolm),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.room.get(room).value?.encryptionAlgorithm shouldBe Megolm
        }
    }

    context(RoomManager::setOwnMembership.name) {
        should("set own membership of a room") {
            store.account.userId.value = alice
            cut.setOwnMembership(
                StateEvent(
                    MemberEventContent(membership = LEAVE),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.room.get(room).value?.membership shouldBe LEAVE
        }
    }
    context(RoomManager::setUnreadMessageCount.name) {
        should("set unread message count for room") {
            store.room.update(room) { simpleRoom.copy(roomId = room) }
            cut.setUnreadMessageCount(room, 24)
            store.room.get(room).value?.unreadMessageCount shouldBe 24
        }
    }
    context(RoomManager::loadMembers.name) {
        should("do nothing when members already loaded") {
            val storedRoom = simpleRoom.copy(roomId = room, membersLoaded = true)
            store.room.update(room) { storedRoom }
            cut.loadMembers(room)
            store.room.get(room).value shouldBe storedRoom
        }
        should("load members") {
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns flowOf(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    1234,
                    stateKey = alice.full
                ),
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event2"),
                    bob,
                    room,
                    1234,
                    stateKey = bob.full
                )
            )
            val storedRoom = simpleRoom.copy(roomId = room, membersLoaded = false)
            store.room.update(room) { storedRoom }
            cut.loadMembers(room)
            store.room.get(room).value?.membersLoaded shouldBe true
            store.roomState.getByStateKey<MemberEventContent>(room, alice.full)?.content?.membership shouldBe JOIN
            store.roomState.getByStateKey<MemberEventContent>(room, bob.full)?.content?.membership shouldBe JOIN
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
                store.roomTimeline.addAll(listOf(timelineEvent))
                cut.getTimelineEvent(eventId, room, this).value shouldBe timelineEvent

                // event gets changed later (e.g. redaction)
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(eventId, room, this)
                delay(20)
                store.roomTimeline.addAll(listOf(timelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    StoredInboundMegolmSession(senderKey, session, room, "pickle")
                }
                delay(20)
                result.value shouldBe timelineEvent
            }
        }
        context("event can be decrypted") {
            should("decrypt event") {
                val expectedDecryptedEvent = MegolmEvent(TextMessageEventContent("decrypted"), room)
                coEvery { olm.events.decryptMegolm(any()) } returns expectedDecryptedEvent
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    StoredInboundMegolmSession(senderKey, session, room, "pickle")
                }
                val result = cut.getTimelineEvent(eventId, room, this).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    decryptedEvent?.getOrNull() shouldBe expectedDecryptedEvent
                }
            }
            should("handle error") {
                coEvery { olm.events.decryptMegolm(any()) } throws DecryptionException.ValidationFailed
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    StoredInboundMegolmSession(senderKey, session, room, "pickle")
                }
                val result = cut.getTimelineEvent(eventId, room, this).take(2).toList()
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
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))

                val result = cut.getTimelineEvent(eventId, room, this)
                delay(20)
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    StoredInboundMegolmSession(senderKey, session, room, "pickle")
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
            store.roomTimeline.addAll(listOf(event2Timeline))
            val scope = CoroutineScope(Dispatchers.Default)
            val result = async {
                cut.getLastTimelineEvent(room, scope).take(3).toList()
            }
            delay(50)
            store.room.update(room) { initialRoom }
            delay(50)
            store.room.update(room) { initialRoom.copy(lastEventId = event1.id) }
            delay(50)
            store.room.update(room) { initialRoom.copy(lastEventId = event2.id) }
            result.await()[0] shouldBe null
            result.await()[1] shouldNotBe null
            result.await()[1]?.value shouldBe null
            result.await()[2]?.value shouldBe event2Timeline
            scope.cancel()
        }
    }
    context(RoomManager::sendMessage.name) {
        should("just save message in store for later use") {
            val content = TextMessageEventContent("hi")
            cut.sendMessage(room) {
                this.content = content
            }
            retry(4, milliseconds(1000), milliseconds(30)) {// we need this, because the cache may not be fast enough
                val outboundMessages = store.roomOutboxMessage.getAll().value
                outboundMessages shouldHaveSize 1
                assertSoftly(outboundMessages.first()) {
                    roomId shouldBe room
                    content shouldBe content
                    transactionId.length shouldBeGreaterThan 12
                }
            }
        }
    }
    context(RoomManager::syncOutboxMessage.name) {
        should("ignore messages from foreign users") {
            store.account.userId.value = UserId("me", "server")
            val roomOutboxMessage = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), true)
            store.roomOutboxMessage.add(roomOutboxMessage)
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                UserId("sender", "server"),
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            store.roomOutboxMessage.getAll().value.first() shouldBe roomOutboxMessage
        }
        should("remove outbox message from us") {
            store.account.userId.value = UserId("me", "server")
            val roomOutboxMessage = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), true)
            store.roomOutboxMessage.add(roomOutboxMessage)
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                UserId("me", "server"),
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            retry(4, milliseconds(1000), milliseconds(30)) { // we need this, because the cache may not be fast enough
                store.roomOutboxMessage.getAll().value.size shouldBe 0
            }
        }
    }
    context(RoomManager::processOutboxMessages.name) {
        should("wait until connected, upload media, send message and mark outbox message as sent") {
            store.room.update(room) { simpleRoom }
            val mxcUrl = "mxc://dino"
            val cacheUrl = "cache://unicorn"
            val mediaUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
            val message1 =
                RoomOutboxMessage(
                    "transaction1", room, ImageMessageEventContent("hi.png", url = cacheUrl),
                    false, mediaUploadProgress
                )
            val message2 = RoomOutboxMessage("transaction2", room, TextMessageEventContent("hi"), false)
            store.roomOutboxMessage.add(message1)
            store.roomOutboxMessage.add(message2)
            coEvery { media.uploadMedia(any(), any()) } returns mxcUrl
            coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("event", "server")
            val syncState = MutableStateFlow(STARTED)
            coEvery { api.sync.currentSyncState } returns syncState

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            until(milliseconds(50), milliseconds(25).fixed()) {
                job.isActive
            }
            syncState.value = RUNNING

            coVerify(timeout = 1000) {
                media.uploadMedia(cacheUrl, mediaUploadProgress)
                api.rooms.sendMessageEvent(room, ImageMessageEventContent("hi.png", url = mxcUrl), "transaction1")
                api.rooms.sendMessageEvent(room, TextMessageEventContent("hi"), "transaction2")
            }
            retry(4, milliseconds(1000), milliseconds(30)) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 2
                outboxMessages[0].wasSent shouldBe true
                outboxMessages[1].wasSent shouldBe true
            }
            job.cancel()
        }
        should("encrypt events in encrypted rooms") {
            store.room.update(room) { simpleRoom.copy(encryptionAlgorithm = Megolm) }
            val message = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), false)
            store.roomOutboxMessage.add(message)
            val encryptionState =
                StateEvent(
                    EncryptionEventContent(),
                    EventId("\$stateEvent"),
                    UserId("sender", "server"),
                    room,
                    1234,
                    stateKey = ""
                )
            store.roomState.update(encryptionState)
            coEvery { api.rooms.sendMessageEvent(any(), any(), any(), any()) } returns EventId("event", "server")
            val megolmEventContent = mockk<MegolmEncryptedEventContent>()
            coEvery { olm.events.encryptMegolm(any(), any(), any()) } returns megolmEventContent
            coEvery { api.rooms.getMembers(any(), any(), any(), any(), any()) } returns flowOf()
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING).asStateFlow()

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            coVerify(timeout = 1000) {
                api.rooms.sendMessageEvent(room, megolmEventContent, "transaction")
                olm.events.encryptMegolm(TextMessageEventContent("hi"), room, EncryptionEventContent())
                api.rooms.getMembers(any(), any(), any(), any(), any())
            }
            retry(4, milliseconds(1000), milliseconds(30)) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 1
                outboxMessages[0].wasSent shouldBe true
            }
            job.cancel()
        }
        should("retry on sending error") {
            store.room.update(room) { simpleRoom }
            val message = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), false)
            store.roomOutboxMessage.add(message)
            coEvery {
                api.rooms.sendMessageEvent(any(), any(), any(), any())
            } throws IllegalArgumentException("wtf") andThen EventId("event", "server")
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(RUNNING).asStateFlow()

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            coVerify(exactly = 2, timeout = 1000) {
                api.rooms.sendMessageEvent(room, TextMessageEventContent("hi"), "transaction")
            }
            retry(4, milliseconds(1000), milliseconds(30)) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 1
                outboxMessages[0].wasSent shouldBe true
            }
            job.cancel()
        }
    }
    context(RoomManager::setRoomAccountData.name) {
        should("set the room's account data") {
            val roomId = RoomId("room1", "server")
            val accountDataEvent = RoomAccountDataEvent(FullyReadEventContent(EventId("event1", "server")), roomId)
            cut.setRoomAccountData(accountDataEvent)
            store.roomAccountData.get<FullyReadEventContent>(roomId, this).value shouldBe accountDataEvent
        }
    }
})