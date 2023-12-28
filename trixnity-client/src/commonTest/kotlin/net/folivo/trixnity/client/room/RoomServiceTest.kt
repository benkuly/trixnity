package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventEncryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.TimelineEvent.TimelineEventContentError
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.ServerAggregation
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RoomServiceTest : ShouldSpec({
    timeout = 15_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var roomEventDecryptionServiceMock: RoomEventEncryptionServiceMock
    val json = createMatrixEventJson()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)

        mediaServiceMock = MediaServiceMock()
        roomEventDecryptionServiceMock = RoomEventEncryptionServiceMock()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomServiceImpl(
            api = api,
            roomStore = roomStore,
            roomStateStore = roomStateStore,
            roomAccountDataStore = roomAccountDataStore,
            roomTimelineStore = roomTimelineStore,
            roomOutboxMessageStore = roomOutboxMessageStore,
            roomEventEncryptionServices = listOf(roomEventDecryptionServiceMock),
            mediaService = mediaServiceMock,
            forgetRoomService = {},
            userInfo = simpleUserInfo,
            timelineEventHandler = TimelineEventHandlerMock(),
            config = MatrixClientConfiguration(),
            typingEventHandler = TypingEventHandlerImpl(api),
            currentSyncState = CurrentSyncState(currentSyncState),
            scope = scope
        )
    }

    afterTest {
        scope.cancel()
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


    context(RoomServiceImpl::getTimelineEvent.name) {
        val eventId = EventId("\$event1")
        val session = "SESSION"
        val senderKey = Key.Curve25519Key(null, "senderKey")
        val encryptedEventContent = MegolmEncryptedMessageEventContent(
            "ciphertext", senderKey, "SENDER", session
        )
        val encryptedTimelineEvent = TimelineEvent(
            event = MessageEvent(
                encryptedEventContent,
                eventId,
                UserId("sender", "server"),
                room,
                1
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null
        )

        context("event not in database") {
            should("try fill gaps until found") {
                val lastEventId = EventId("\$eventWorld")
                roomStore.update(room) { simpleRoom.copy(lastEventId = lastEventId) }
                currentSyncState.value = RUNNING
                val event = MessageEvent(
                    TextMessageEventContent("hello"),
                    eventId,
                    UserId("sender", "server"),
                    room,
                    1
                )
                roomTimelineStore.addAll(
                    listOf(
                        TimelineEvent(
                            event = MessageEvent(
                                TextMessageEventContent("world"),
                                lastEventId,
                                UserId("sender", "server"),
                                room,
                                0
                            ),
                            previousEventId = null,
                            nextEventId = null,
                            gap = TimelineEvent.Gap.GapBefore("start")
                        )
                    )
                )
                val timelineEventFlow = cut.getTimelineEvent(room, eventId)
                roomTimelineStore.addAll(
                    listOf(
                        TimelineEvent(
                            event = event,
                            previousEventId = null,
                            nextEventId = lastEventId,
                            gap = TimelineEvent.Gap.GapBefore("end")
                        )
                    )
                )
                timelineEventFlow.filterNotNull().first() shouldBe
                        TimelineEvent(
                            event = event,
                            previousEventId = null,
                            nextEventId = lastEventId,
                            gap = TimelineEvent.Gap.GapBefore("end")
                        )
            }
        }
        context("should just return event") {
            withData(
                mapOf(
                    "with already encrypted event" to encryptedTimelineEvent.copy(
                        content = Result.success(TextMessageEventContent("hi"))
                    ),
                    "with encryption error" to encryptedTimelineEvent.copy(
                        content = Result.failure(TimelineEventContentError.DecryptionTimeout)
                    ),
                    "without RoomEvent" to encryptedTimelineEvent.copy(
                        event = nameEvent(1)
                    ),
                    "without MegolmEncryptedEventContent" to encryptedTimelineEvent.copy(
                        event = textEvent(1)
                    )
                )
            ) { timelineEvent ->
                roomTimelineStore.addAll(listOf(timelineEvent))
                cut.getTimelineEvent(room, eventId).first() shouldBe timelineEvent

                // event gets changed later (e.g. redaction)
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(room, eventId)
                delay(20)
                roomTimelineStore.addAll(listOf(timelineEvent))
                delay(20)
                result.first() shouldBe timelineEvent
            }
        }
        context("event can be decrypted") {
            should("decrypt event") {
                val expectedDecryptedEvent = TextMessageEventContent("decrypted")
                roomEventDecryptionServiceMock.returnDecrypt = Result.success(expectedDecryptedEvent)
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(room, eventId)
                    .first { it?.content?.getOrNull() != null }
                assertSoftly(result) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.getOrNull() shouldBe expectedDecryptedEvent
                }
            }
            should("decrypt event only once") {
                val expectedDecryptedEvent = TextMessageEventContent("decrypted")
                roomEventDecryptionServiceMock.returnDecrypt = Result.success(expectedDecryptedEvent)
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                (1..300).map {
                    async {
                        cut.getTimelineEvent(room, eventId)
                            .first { it?.content?.getOrNull() != null }
                    }
                }.awaitAll()
                roomEventDecryptionServiceMock.decryptCounter shouldBe 1
            }
            should("timeout when decryption takes too long") {
                roomEventDecryptionServiceMock.decryptDelay = 10.seconds
                roomEventDecryptionServiceMock.returnDecrypt = null
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = async { cut.getTimelineEvent(room, eventId) { decryptionTimeout = ZERO }.collect() }
                result.job.children.count() shouldBe 0 // there are no decryption jobs
                result.cancel()
            }
            should("retry on decryption timeout") {
                roomEventDecryptionServiceMock.decryptDelay = 10.seconds
                roomEventDecryptionServiceMock.returnDecrypt = null
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                cut.getTimelineEvent(room, eventId) { decryptionTimeout = 50.milliseconds }.first()
                cut.getTimelineEvent(room, eventId) { decryptionTimeout = 50.milliseconds }.first()
                roomEventDecryptionServiceMock.decryptCounter shouldBe 2
            }
            should("handle error") {
                roomEventDecryptionServiceMock.returnDecrypt =
                    Result.failure(OlmEncryptionService.DecryptMegolmError.MegolmKeyUnknownMessageIndex)
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(room, eventId)
                    .first { it?.content?.isFailure == true }
                assertSoftly(result) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.exceptionOrNull() shouldBe
                            TimelineEventContentError.DecryptionError(OlmEncryptionService.DecryptMegolmError.MegolmKeyUnknownMessageIndex)
                }
            }
        }
        context("content has been replaced") {
            val replaceTimelineEvent = TimelineEvent(
                event = MessageEvent(
                    encryptedEventContent, // in reality there is a relatesTo
                    EventId("\$event2"),
                    UserId("sender", "server"),
                    room,
                    1
                ),
                content = Result.success(
                    TextMessageEventContent(
                        "*edited hi",
                        relatesTo = RelatesTo.Replace(
                            EventId("\$event1"),
                            TextMessageEventContent("edited hi")
                        )
                    )
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val timelineEvent = TimelineEvent(
                event = MessageEvent(
                    encryptedEventContent,
                    EventId("\$event1"),
                    UserId("sender", "server"),
                    room,
                    1,
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        relations = mapOf(
                            RelationType.Replace to ServerAggregation.Replace(
                                replaceTimelineEvent.eventId,
                                replaceTimelineEvent.event.sender,
                                replaceTimelineEvent.event.originTimestamp
                            )
                        )
                    )
                ),
                content = Result.success(TextMessageEventContent("hi")),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            should("replace content with content of other timeline event") {
                roomTimelineStore.addAll(listOf(timelineEvent, replaceTimelineEvent))
                cut.getTimelineEvent(room, eventId).first() shouldBe timelineEvent.copy(
                    content = Result.success(TextMessageEventContent("edited hi"))
                )
            }
            should("not replace content when disabled") {
                roomTimelineStore.addAll(listOf(timelineEvent, replaceTimelineEvent))
                cut.getTimelineEvent(room, eventId) { allowReplaceContent = false }.first() shouldBe timelineEvent.copy(
                    content = Result.success(TextMessageEventContent("hi"))
                )
            }
        }
    }
    context(RoomServiceImpl::getLastTimelineEvent.name) {
        should("return last event of room") {
            val initialRoom = Room(room, lastEventId = null)
            val event1 = textEvent(1)
            val event2 = textEvent(2)
            val event2Timeline = TimelineEvent(
                event = event2,
                content = null,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            roomTimelineStore.addAll(listOf(event2Timeline))
            val result = async {
                cut.getLastTimelineEvent(room).take(3).toList()
            }
            delay(50)
            roomStore.update(room) { initialRoom }
            delay(50)
            roomStore.update(room) { initialRoom.copy(lastEventId = event1.id) }
            delay(50)
            roomStore.update(room) { initialRoom.copy(lastEventId = event2.id) }
            result.await()[0] shouldBe null
            withTimeoutOrNull(100.milliseconds) { result.await()[1].shouldNotBeNull().first() } shouldBe null
            result.await()[2].shouldNotBeNull().first() shouldBe event2Timeline
        }
    }
    context(RoomServiceImpl::sendMessage.name) {
        should("just save message in store for later use") {
            val content = TextMessageEventContent("hi")
            cut.sendMessage(room) {
                contentBuilder = { _, _, _ -> content }
            }
            retry(100, 3_000.milliseconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboundMessages = roomOutboxMessageStore.getAll().flattenValues().first()
                outboundMessages shouldHaveSize 1
                assertSoftly(outboundMessages.first()) {
                    roomId shouldBe room
                    content shouldBe content
                    transactionId.length shouldBeGreaterThan 12
                }
            }
        }
    }
})