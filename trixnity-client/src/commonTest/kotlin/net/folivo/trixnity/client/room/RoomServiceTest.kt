package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.assertions.until.fixed
import io.kotest.assertions.until.until
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.clientserverapi.client.SyncState.STARTED
import net.folivo.trixnity.clientserverapi.model.media.FileTransferProgress
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RoomServiceTest : ShouldSpec({
    timeout = 30_000
    val alice = UserId("alice", "server")
    val room = simpleRoom.roomId
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var users: UserServiceMock
    lateinit var olmEventService: OlmEventServiceMock
    lateinit var keyBackup: KeyBackupServiceMock
    lateinit var media: MediaServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        users = UserServiceMock()
        olmEventService = OlmEventServiceMock()
        keyBackup = KeyBackupServiceMock()
        media = MediaServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = RoomService(
            alice, store, api, olmEventService, keyBackup, users, media, currentSyncState,
            MatrixClientConfiguration()
        )
    }

    afterTest {
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

    context(RoomService::setLastEventId.name) {
        should("set last event from room event") {
            cut.setLastEventId(textEvent(24))
            store.room.get(room).value?.lastEventId shouldBe EventId("\$event24")
        }
        should("set last event from state event") {
            cut.setLastEventId(
                StateEvent(
                    MemberEventContent(membership = JOIN),
                    EventId("\$event1"),
                    alice,
                    room,
                    25,
                    stateKey = alice.full
                )
            )
            store.room.get(room).value?.lastEventId shouldBe EventId("\$event1")
        }
    }

    context(RoomService::setLastRelevantEvent.name) {
        should("set last message event") {
            cut.handleSyncResponse(
                Sync.Response(
                    room = Sync.Response.Rooms(
                        join = mapOf(
                            room to Sync.Response.Rooms.JoinedRoom(
                                timeline = Sync.Response.Rooms.Timeline(
                                    events = listOf(
                                        StateEvent(
                                            CreateEventContent(UserId("user1", "localhost")),
                                            EventId("event1"),
                                            UserId("user1", "localhost"),
                                            room,
                                            0,
                                            stateKey = ""
                                        ),
                                        MessageEvent(
                                            TextMessageEventContent("Hello!"),
                                            EventId("event2"),
                                            UserId("user1", "localhost"),
                                            room,
                                            5,
                                        ),
                                        StateEvent(
                                            AvatarEventContent("mxc://localhost/123456"),
                                            EventId("event3"),
                                            UserId("user1", "localhost"),
                                            room,
                                            10,
                                            stateKey = ""
                                        ),
                                    ), previousBatch = "abcdef"
                                )
                            )
                        )
                    ), nextBatch = "123456"
                )
            )
            store.room.get(room).value?.lastRelevantEventId shouldBe EventId("event2")
        }
    }

    context(RoomService::removeOldOutboxMessages.name) {
        should("remove old outbox messages") {
            val content = TextMessageEventContent("")
            val outbox1 = RoomOutboxMessage("transaction1", room, content)
            val outbox2 = RoomOutboxMessage("transaction2", room, content, Clock.System.now() - 10.seconds)
            val outbox3 = RoomOutboxMessage("transaction3", room, content, Clock.System.now())

            store.roomOutboxMessage.update(outbox1.transactionId) { outbox1 }
            store.roomOutboxMessage.update(outbox2.transactionId) { outbox2 }
            store.roomOutboxMessage.update(outbox3.transactionId) { outbox3 }

            retry(100, 3_000.milliseconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                cut.removeOldOutboxMessages()
                store.roomOutboxMessage.getAll().value shouldContainExactly listOf(outbox1, outbox3)
            }
        }
    }

    context(RoomService::redactTimelineEvent.name) {
        context("with existent event") {
            should("redact room event") {
                val event1 = textEvent(1)
                val event2 = textEvent(2)
                val event3 = textEvent(3)
                store.roomTimeline.addAll(
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
                            content = Result.failure(DecryptionException.ValidationFailed),
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
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
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
                    content shouldBe null
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
                            content = null,
                            roomId = room,
                            eventId = event1.id,
                            previousEventId = null,
                            nextEventId = event2.id,
                            gap = null
                        ),
                        TimelineEvent(
                            event = event2,
                            content = Result.failure(DecryptionException.ValidationFailed),
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
                assertSoftly(store.roomTimeline.get(event2.id, room)!!) {
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
                    content shouldBe null
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
                    content = Result.failure(DecryptionException.ValidationFailed),
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
                store.roomTimeline.get(EventId("\$incorrectlyEvent"), room) shouldBe null
                store.roomTimeline.get(timelineEvent1.eventId, room) shouldBe timelineEvent1
                store.roomTimeline.get(timelineEvent2.eventId, room) shouldBe timelineEvent2
            }
        }
    }

    context(RoomService::setEncryptionAlgorithm.name) {
        should("update set encryption algorithm") {
            store.room.update(room) { simpleRoom.copy(membersLoaded = true) }
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
            store.room.get(room).value?.membersLoaded shouldBe false
        }
    }

    context(RoomService::setOwnMembership.name) {
        should("set own membership of a room") {
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
    context(RoomService::setUnreadMessageCount.name) {
        should("set unread message count for room") {
            store.room.update(room) { simpleRoom.copy(roomId = room) }
            cut.setUnreadMessageCount(room, 24)
            store.room.get(room).value?.unreadMessageCount shouldBe 24
        }
    }
    context(RoomService::getTimelineEvent.name) {
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
        val storedSession = StoredInboundMegolmSession(
            senderKey, session, room, 1, hasBeenBackedUp = false, isTrusted = false,
            senderSigningKey = Key.Ed25519Key(null, "ed"), forwardingCurve25519KeyChain = listOf(), pickled = "pickle"
        )

        context("should just return event") {
            withData(
                mapOf(
                    "with already encrypted event" to encryptedTimelineEvent.copy(
                        content = Result.success(TextMessageEventContent("hi"))
                    ),
                    "with encryption error" to encryptedTimelineEvent.copy(
                        content = Result.failure(DecryptionException.ValidationFailed)
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
                store.olm.updateInboundMegolmSession(senderKey, session, room) { storedSession }
                delay(20)
                result.value shouldBe timelineEvent
            }
        }
        context("event can be decrypted") {
            should("decrypt event") {
                val expectedDecryptedEvent = DecryptedMegolmEvent(TextMessageEventContent("decrypted"), room)
                olmEventService.returnDecryptMegolm.add { expectedDecryptedEvent }
                store.keys.updateDeviceKeys(encryptedTimelineEvent.event.sender) {
                    mapOf(
                        encryptedEventContent.deviceId to StoredDeviceKeys(
                            Signed(DeviceKeys(alice, "", setOf(), keysOf()), null),
                            Valid(true)
                        )
                    )
                }
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) { storedSession }
                val result = cut.getTimelineEvent(eventId, room, this).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.getOrNull() shouldBe expectedDecryptedEvent.content
                }
            }
            should("timeout when decryption takes too long") {
                store.keys.updateDeviceKeys(encryptedTimelineEvent.event.sender) {
                    mapOf(
                        encryptedEventContent.deviceId to StoredDeviceKeys(
                            Signed(DeviceKeys(alice, "", setOf(), keysOf()), null),
                            Valid(true)
                        )
                    )
                }
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                val result = async { cut.getTimelineEvent(eventId, room, this, 0.seconds).first() }
                // await would suspend infinite, when there is INFINITE timeout, because the coroutine spawned within async would wait for megolm keys
                result.await() shouldBe encryptedTimelineEvent
                result.job.children.count() shouldBe 0
            }
            should("handle error") {
                olmEventService.returnDecryptMegolm.add { throw DecryptionException.ValidationFailed }
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) { storedSession }
                val result = cut.getTimelineEvent(eventId, room, this).take(2).toList()
                result[0] shouldBe encryptedTimelineEvent
                assertSoftly(result[1]) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.exceptionOrNull() shouldBe DecryptionException.ValidationFailed
                }
            }
            should("wait for olm session and ask key backup for it") {
                val expectedDecryptedEvent = DecryptedMegolmEvent(TextMessageEventContent("decrypted"), room)
                olmEventService.returnDecryptMegolm.add { expectedDecryptedEvent }
                store.keys.updateDeviceKeys(encryptedTimelineEvent.event.sender) {
                    mapOf(
                        encryptedEventContent.deviceId to StoredDeviceKeys(
                            Signed(DeviceKeys(alice, "", setOf(), keysOf()), null),
                            Valid(true)
                        )
                    )
                }
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))

                val result = cut.getTimelineEvent(eventId, room, this)
                delay(20)
                store.olm.updateInboundMegolmSession(senderKey, session, room) { storedSession }
                delay(20)
                assertSoftly(result.value) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.getOrNull() shouldBe expectedDecryptedEvent.content
                }
                keyBackup.loadMegolmSessionCalled.value.first() shouldBe Triple(room, session, senderKey)
            }
            should("wait for olm session and ask key backup for it when existing session does not known the index") {
                val expectedDecryptedEvent = DecryptedMegolmEvent(TextMessageEventContent("decrypted"), room)
                olmEventService.returnDecryptMegolm.add { throw OlmLibraryException("OLM_UNKNOWN_MESSAGE_INDEX") }
                olmEventService.returnDecryptMegolm.add { expectedDecryptedEvent }
                store.keys.updateDeviceKeys(encryptedTimelineEvent.event.sender) {
                    mapOf(
                        encryptedEventContent.deviceId to StoredDeviceKeys(
                            Signed(DeviceKeys(alice, "", setOf(), keysOf()), null),
                            Valid(true)
                        )
                    )
                }
                store.roomTimeline.addAll(listOf(encryptedTimelineEvent))
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    storedSession.copy(firstKnownIndex = 4)
                }
                val result = cut.getTimelineEvent(eventId, room, this)

                delay(50)
                store.olm.updateInboundMegolmSession(senderKey, session, room) {
                    storedSession.copy(firstKnownIndex = 3)
                }
                val resultValue = result.filterNotNull().first { it.content?.getOrNull() != null }
                assertSoftly(resultValue) {
                    this.event shouldBe encryptedTimelineEvent.event
                    this.content?.getOrNull() shouldBe expectedDecryptedEvent.content
                }
                keyBackup.loadMegolmSessionCalled.value.size shouldBe 1
                keyBackup.loadMegolmSessionCalled.value.first() shouldBe Triple(room, session, senderKey)
            }
        }
    }
    context(RoomService::getLastTimelineEvent.name) {
        should("return last event of room") {
            val initialRoom = Room(room, lastEventId = null)
            val event1 = textEvent(1)
            val event2 = textEvent(2)
            val event2Timeline = TimelineEvent(
                event = event2,
                content = null,
                roomId = room,
                eventId = event2.id,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            store.roomTimeline.addAll(listOf(event2Timeline))
            val scope = CoroutineScope(Dispatchers.Default)
            val result = async {
                cut.getLastTimelineEvent(room).take(3).toList()
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
    context(RoomService::sendMessage.name) {
        should("just save message in store for later use") {
            val content = TextMessageEventContent("hi")
            cut.sendMessage(room) {
                this.content = content
            }
            retry(100, 3_000.milliseconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
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
    context(RoomService::syncOutboxMessage.name) {
        should("ignore messages from foreign users") {
            val roomOutboxMessage =
                RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), Clock.System.now())
            store.roomOutboxMessage.update(roomOutboxMessage.transactionId) { roomOutboxMessage }
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                UserId("other", "server"),
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                store.roomOutboxMessage.getAll().value shouldContainExactly listOf(roomOutboxMessage)
            }
        }
        should("remove outbox message from us") {
            val roomOutboxMessage =
                RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), Clock.System.now())
            store.roomOutboxMessage.update(roomOutboxMessage.transactionId) { roomOutboxMessage }
            val event: Event<MessageEventContent> = MessageEvent(
                TextMessageEventContent("hi"),
                EventId("\$event"),
                alice,
                room,
                1234,
                UnsignedMessageEventData(transactionId = "transaction")
            )
            cut.syncOutboxMessage(event)
            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                store.roomOutboxMessage.getAll().value.size shouldBe 0
            }
        }
    }
    context(RoomService::processOutboxMessages.name) {
        should("wait until connected, upload media, send message and mark outbox message as sent") {
            store.room.update(room) { simpleRoom }
            val mxcUrl = "mxc://dino"
            val cacheUrl = "cache://unicorn"
            val mediaUploadProgress = MutableStateFlow<FileTransferProgress?>(null)
            val message1 =
                RoomOutboxMessage(
                    "transaction1", room, ImageMessageEventContent("hi.png", url = cacheUrl),
                    null, mediaUploadProgress = mediaUploadProgress
                )
            val message2 = RoomOutboxMessage("transaction2", room, TextMessageEventContent("hi"), null)
            store.roomOutboxMessage.update(message1.transactionId) { message1 }
            store.roomOutboxMessage.update(message2.transactionId) { message2 }
            media.returnUploadMedia = Result.success(mxcUrl)
            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendMessageEvent(room.e(), "m.room.message", "transaction1"),
                ) {
                    it shouldBe ImageMessageEventContent("hi.png", url = mxcUrl)
                    SendEventResponse(EventId("event"))
                }
                matrixJsonEndpoint(
                    json, mappings,
                    SendMessageEvent(room.e(), "m.room.message", "transaction2"),
                ) {
                    it shouldBe TextMessageEventContent("hi")
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }
            currentSyncState.value = STARTED

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            until(50.milliseconds, 25.milliseconds.fixed()) {
                job.isActive
            }
            currentSyncState.value = RUNNING

            media.uploadMediaCalled.first { it == cacheUrl }
            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 2
                outboxMessages[0].sentAt shouldNotBe null
                outboxMessages[1].sentAt shouldNotBe null
            }
            sendMessageEventCalled shouldBe true
            job.cancel()
        }
        should("encrypt events in encrypted rooms") {
            currentSyncState.value = RUNNING
            store.room.update(room) { simpleRoom.copy(encryptionAlgorithm = Megolm, membersLoaded = true) }
            val message = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), null)
            store.roomOutboxMessage.update(message.transactionId) { message }
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
            val megolmEventContent =
                MegolmEncryptedEventContent("cipher", Key.Curve25519Key(null, "key"), "device", "session")
            var sendMessageEventCalled = false
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendMessageEvent(room.e(), "m.room.encrypted", "transaction"),
                ) {
                    it shouldBe megolmEventContent
                    sendMessageEventCalled = true
                    SendEventResponse(EventId("event"))
                }
            }
            olmEventService.returnEncryptMegolm = { megolmEventContent }

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            users.loadMembersCalled.first { it == room }
            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 1
                outboxMessages[0].sentAt shouldNotBe null
            }
            sendMessageEventCalled shouldBe true
            job.cancel()
        }
        should("retry on sending error") {
            store.room.update(room) { simpleRoom }
            val message = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), null)
            store.roomOutboxMessage.update(message.transactionId) { message }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendMessageEvent(room.e(), "m.room.message", "transaction"),
                ) {
                    throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown())
                }
                matrixJsonEndpoint(
                    json, mappings,
                    SendMessageEvent(room.e(), "m.room.message", "transaction"),
                ) {
                    SendEventResponse(EventId("event"))
                }
            }
            currentSyncState.value = RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 1
                outboxMessages[0].sentAt shouldNotBe null
            }
            job.cancel()
        }

        should("not retry infinite on sending error") {
            store.room.update(room) { simpleRoom }
            val message = RoomOutboxMessage("transaction", room, TextMessageEventContent("hi"), null)
            store.roomOutboxMessage.update(message.transactionId) { message }
            apiConfig.endpoints {
                repeat(3) {
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(room.e(), "m.room.message", "transaction"),
                    ) {
                        throw MatrixServerException(HttpStatusCode.InternalServerError, ErrorResponse.Unknown())
                    }
                }
            }
            currentSyncState.value = RUNNING

            val job = launch(Dispatchers.Default) { cut.processOutboxMessages(store.roomOutboxMessage.getAll()) }

            retry(100, 3_000.milliseconds, 30.milliseconds) { // we need this, because the cache may not be fast enough
                val outboxMessages = store.roomOutboxMessage.getAll().value
                outboxMessages shouldHaveSize 1
                outboxMessages[0].sentAt shouldBe null
                outboxMessages[0].reachedMaxRetryCount shouldBe true
            }
            job.cancel()
        }
    }
})