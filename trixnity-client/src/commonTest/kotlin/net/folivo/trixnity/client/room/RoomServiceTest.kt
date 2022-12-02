package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.MediaServiceMock
import net.folivo.trixnity.client.mocks.RoomEventDecryptionServiceMock
import net.folivo.trixnity.client.mocks.TimelineEventHandlerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.RUNNING
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import kotlin.test.assertNotNull
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
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var roomEventDecryptionServiceMock: RoomEventDecryptionServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)
    val thisUser = UserId("thisUser")
    val userInfo = UserInfo(
        thisUser,
        "deviceId",
        signingPublicKey = Key.Ed25519Key(value = ""),
        Key.Curve25519Key(value = "")
    )

    lateinit var cut: RoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)

        mediaServiceMock = MediaServiceMock()
        roomEventDecryptionServiceMock = RoomEventDecryptionServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = RoomServiceImpl(
            api,
            roomStore, roomStateStore, roomAccountDataStore, roomTimelineStore, roomOutboxMessageStore,
            listOf(roomEventDecryptionServiceMock),
            mediaServiceMock,
            TimelineEventHandlerMock(),
            CurrentSyncState(currentSyncState),
            userInfo,
            scope
        )
    }

    afterTest {
        scope.cancel()
    }

    suspend fun storeTimeline(vararg events: Event.RoomEvent<*>) = events.map {
        roomTimelineStore.get(it.id, it.roomId)
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
                            roomId = room,
                            eventId = lastEventId,
                            previousEventId = null,
                            nextEventId = null,
                            gap = TimelineEvent.Gap.GapBefore("start")
                        )
                    )
                )
                val timelineEventFlow = cut.getTimelineEvent(eventId, room)
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
                        content = Result.failure(DecryptionException.ValidationFailed(""))
                    ),
                    "without RoomEvent" to encryptedTimelineEvent.copy(
                        event = nameEvent(24)
                    ),
                    "without MegolmEncryptedEventContent" to encryptedTimelineEvent.copy(
                        event = textEvent(48)
                    )
                )
            ) { timelineEvent ->
                roomTimelineStore.addAll(listOf(timelineEvent))
                cut.getTimelineEvent(eventId, room).first() shouldBe timelineEvent

                // event gets changed later (e.g. redaction)
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(eventId, room)
                delay(20)
                roomTimelineStore.addAll(listOf(timelineEvent))
                delay(20)
                result.first() shouldBe timelineEvent
            }
        }
        context("event can be decrypted") {
            should("decrypt event") {
                val expectedDecryptedEvent = TextMessageEventContent("decrypted")
                roomEventDecryptionServiceMock.returnDecrypt = { Result.success(expectedDecryptedEvent) }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(eventId, room)
                    .first { it?.content?.getOrNull() != null }
                assertSoftly(result) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.getOrNull() shouldBe expectedDecryptedEvent
                }
            }
            should("decrypt event only once") {
                val expectedDecryptedEvent = TextMessageEventContent("decrypted")
                roomEventDecryptionServiceMock.returnDecrypt = { Result.success(expectedDecryptedEvent) }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                (0..99).map {
                    async {
                        cut.getTimelineEvent(eventId, room)
                            .first { it?.content?.getOrNull() != null }
                    }
                }.awaitAll()
                roomEventDecryptionServiceMock.decryptCounter shouldBe 1
            }
            should("timeout when decryption takes too long") {
                roomEventDecryptionServiceMock.returnDecrypt = {
                    delay(10.seconds)
                    null
                }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = async { cut.getTimelineEvent(eventId, room, 0.seconds).first() }
                // await would suspend infinite, when there is INFINITE timeout, because the coroutine spawned within async would wait for megolm keys
                result.await() shouldBe encryptedTimelineEvent
                result.job.children.count() shouldBe 0
            }
            should("handle error") {
                roomEventDecryptionServiceMock.returnDecrypt =
                    { Result.failure(DecryptionException.ValidationFailed("")) }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(eventId, room)
                    .first { it?.content?.isFailure == true }
                assertSoftly(result) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.exceptionOrNull() shouldBe DecryptionException.ValidationFailed("")
                }
            }
        }
        context("content has been replaced") {
            should("replace content with content of other timeline event") {
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
                            aggregations = Aggregations(
                                mapOf(
                                    RelationType.Replace to Aggregation.Replace(
                                        replaceTimelineEvent.eventId,
                                        replaceTimelineEvent.event.sender,
                                        replaceTimelineEvent.event.originTimestamp
                                    )
                                )
                            )
                        )
                    ),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null
                )
                roomTimelineStore.addAll(listOf(timelineEvent, replaceTimelineEvent))
                cut.getTimelineEvent(eventId, room).first() shouldBe timelineEvent.copy(
                    content = Result.success(TextMessageEventContent("edited hi"))
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
                roomId = room,
                eventId = event2.id,
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
            result.await()[1] shouldNotBe null
            result.await()[1]?.first() shouldBe null
            result.await()[2]?.first() shouldBe event2Timeline
        }
    }
    context(RoomServiceImpl::sendMessage.name) {
        should("just save message in store for later use") {
            val content = TextMessageEventContent("hi")
            cut.sendMessage(room) {
                contentBuilder = { content }
            }
            retry(100, 3_000.milliseconds, 30.milliseconds) {// we need this, because the cache may not be fast enough
                val outboundMessages = roomOutboxMessageStore.getAll().value
                outboundMessages shouldHaveSize 1
                assertSoftly(outboundMessages.first()) {
                    roomId shouldBe room
                    content shouldBe content
                    transactionId.length shouldBeGreaterThan 12
                }
            }
        }
    }
    context(RoomServiceImpl::canBeRedacted.name) {
        val timelineEventByUser = TimelineEvent(
            event = MessageEvent(
                content = TextMessageEventContent(body = "Hi"),
                id = EventId("4711"),
                sender = thisUser,
                roomId = room,
                originTimestamp = 0L,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )
        val timelineEventByOtherUser = TimelineEvent(
            event = MessageEvent(
                content = TextMessageEventContent(body = "Hi"),
                id = EventId("4711"),
                sender = UserId("otherUser"),
                roomId = room,
                originTimestamp = 0L,
            ),
            previousEventId = null,
            nextEventId = null,
            gap = null,
        )

        should("return true if it is the event of the user and the user's power level is at least as high as the needed event redaction level") {
            roomStateStore.update(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            thisUser to 40,
                        ),
                        events = mapOf(
                            "m.room.redaction" to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = thisUser,
                    roomId = room,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            cut.canBeRedacted(
                timelineEvent = timelineEventByUser,
            ).firstOrNull() shouldBe true
        }

        should("return true if it is the event of another user but the user's power level is at least as high as the needed redaction power level") {
            roomStateStore.update(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            thisUser to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = thisUser,
                    roomId = room,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            cut.canBeRedacted(
                timelineEvent = timelineEventByOtherUser,
            ).firstOrNull() shouldBe true
        }
        should("return false if the user has no high enough power level for event redactions") {
            roomStateStore.update(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            thisUser to 20,
                        ),
                        events = mapOf(
                            "m.room.redaction" to 30,
                        )
                    ),
                    id = EventId("eventId"),
                    sender = thisUser,
                    roomId = room,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            cut.canBeRedacted(
                timelineEvent = timelineEventByUser,
            ).firstOrNull() shouldBe false
        }
        should("return false if the user has no high enough power level for redactions of events of other users") {
            roomStateStore.update(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            thisUser to 20,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = thisUser,
                    roomId = room,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            cut.canBeRedacted(
                timelineEvent = timelineEventByOtherUser,
            ).firstOrNull() shouldBe false
        }

        should("not allow to redact an already redacted event") {
            roomStateStore.update(
                StateEvent(
                    content = PowerLevelsEventContent(
                        users = mapOf(
                            thisUser to 40,
                        ),
                        redact = 30,
                    ),
                    id = EventId("eventId"),
                    sender = thisUser,
                    roomId = room,
                    originTimestamp = 0L,
                    stateKey = "",
                )
            )
            cut.canBeRedacted(
                timelineEvent = TimelineEvent(
                    event = MessageEvent(
                        content = RedactedMessageEventContent(eventType = "redacted"),
                        id = EventId("event"),
                        sender = thisUser,
                        roomId = room,
                        originTimestamp = 0L
                    ),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null,
                ),
            ).firstOrNull() shouldBe false
        }

        context("react to changes in the power levels") {
            should("react to changes in the user's power levels") {
                roomStateStore.update(
                    StateEvent(
                        content = PowerLevelsEventContent(
                            users = mapOf(
                                thisUser to 40,
                            ),
                            redact = 30,
                        ),
                        id = EventId("eventId"),
                        sender = thisUser,
                        roomId = room,
                        originTimestamp = 0L,
                        stateKey = "",
                    )
                )
                val resultFlow = cut.canBeRedacted(
                    timelineEvent = timelineEventByOtherUser,
                )
                resultFlow.first() shouldBe true
                roomStateStore.update(
                    StateEvent(
                        content = PowerLevelsEventContent(
                            users = mapOf(
                                thisUser to 20,
                            ),
                            redact = 30,
                        ),
                        id = EventId("eventId"),
                        sender = thisUser,
                        roomId = room,
                        originTimestamp = 0L,
                        stateKey = "",
                    )
                )
                resultFlow.first() shouldBe false
            }
        }
    }
})