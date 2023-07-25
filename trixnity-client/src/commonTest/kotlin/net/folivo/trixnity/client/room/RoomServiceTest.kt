package net.folivo.trixnity.client.room

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.retry
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
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
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptionException
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RoomServiceTest : ShouldSpec({
    timeout = 15_000

    val room = simpleRoom.roomId
    lateinit var roomStore: RoomStore
    lateinit var roomUserStore: RoomUserStore
    lateinit var roomStateStore: RoomStateStore
    lateinit var roomAccountDataStore: RoomAccountDataStore
    lateinit var roomTimelineStore: RoomTimelineStore
    lateinit var roomOutboxMessageStore: RoomOutboxMessageStore
    lateinit var scope: CoroutineScope
    lateinit var mediaServiceMock: MediaServiceMock
    lateinit var roomEventDecryptionServiceMock: RoomEventDecryptionServiceMock
    val json = createMatrixEventJson()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: RoomServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        roomStore = getInMemoryRoomStore(scope)
        roomUserStore = getInMemoryRoomUserStore(scope)
        roomStateStore = getInMemoryRoomStateStore(scope)
        roomAccountDataStore = getInMemoryRoomAccountDataStore(scope)
        roomTimelineStore = getInMemoryRoomTimelineStore(scope)
        roomOutboxMessageStore = getInMemoryRoomOutboxMessageStore(scope)

        mediaServiceMock = MediaServiceMock()
        roomEventDecryptionServiceMock = RoomEventDecryptionServiceMock()
        val (api, _) = mockMatrixClientServerApiClient(json)
        cut = RoomServiceImpl(
            api,
            roomStore, roomUserStore, roomStateStore, roomAccountDataStore, roomTimelineStore, roomOutboxMessageStore,
            listOf(roomEventDecryptionServiceMock),
            mediaServiceMock,
            simpleUserInfo,
            TimelineEventHandlerMock(),
            TypingEventHandler(api),
            CurrentSyncState(currentSyncState),
            scope
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
                roomEventDecryptionServiceMock.returnDecrypt = { Result.success(expectedDecryptedEvent) }
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
                roomEventDecryptionServiceMock.returnDecrypt = { Result.success(expectedDecryptedEvent) }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                (0..99).map {
                    async {
                        cut.getTimelineEvent(room, eventId)
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
                val result = async { cut.getTimelineEvent(room, eventId) { decryptionTimeout = ZERO }.first() }
                // await would suspend infinite, when there is INFINITE timeout, because the coroutine spawned within async would wait for megolm keys
                result.await() shouldBe encryptedTimelineEvent
                result.job.children.count() shouldBe 0
            }
            should("handle error") {
                roomEventDecryptionServiceMock.returnDecrypt =
                    { Result.failure(DecryptionException.ValidationFailed("")) }
                roomTimelineStore.addAll(listOf(encryptedTimelineEvent))
                val result = cut.getTimelineEvent(room, eventId)
                    .first { it?.content?.isFailure == true }
                assertSoftly(result) {
                    assertNotNull(this)
                    event shouldBe encryptedTimelineEvent.event
                    content?.exceptionOrNull() shouldBe DecryptionException.ValidationFailed("")
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
                        relations = Relations(
                            mapOf(
                                RelationType.Replace to ServerAggregation.Replace(
                                    replaceTimelineEvent.eventId,
                                    replaceTimelineEvent.event.sender,
                                    replaceTimelineEvent.event.originTimestamp
                                )
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
    context(RoomServiceImpl::forgetRoom.name) {
        should("forget rooms when membershipt is leave") {
            roomStore.update(room) { simpleRoom.copy(room, membership = Membership.LEAVE) }

            fun timelineEvent(roomId: RoomId, i: Int) =
                TimelineEvent(
                    MessageEvent(
                        TextMessageEventContent("$i"),
                        EventId("$i"),
                        UserId("sender", "server"),
                        roomId,
                        1234L,
                    ),
                    previousEventId = null,
                    nextEventId = null,
                    gap = null,
                )
            roomTimelineStore.addAll(
                listOf(
                    timelineEvent(room, 1),
                    timelineEvent(room, 2),
                )
            )

            fun timelineEventRelation(roomId: RoomId, i: Int) =
                TimelineEventRelation(roomId, EventId("r$i"), RelationType.Replace, EventId("$i"))
            roomTimelineStore.addRelation(timelineEventRelation(room, 1))
            roomTimelineStore.addRelation(timelineEventRelation(room, 2))

            fun stateEvent(roomId: RoomId, i: Int) =
                Event.StateEvent(
                    MemberEventContent(membership = Membership.JOIN),
                    EventId("$i"),
                    UserId("sender", "server"),
                    roomId,
                    1234L,
                    stateKey = "$i",
                )
            roomStateStore.save(stateEvent(room, 1))
            roomStateStore.save(stateEvent(room, 2))

            fun roomAccountDataEvent(roomId: RoomId, i: Int) =
                Event.RoomAccountDataEvent(
                    FullyReadEventContent(EventId("$i")),
                    roomId,
                    key = "$i",
                )
            roomAccountDataStore.save(roomAccountDataEvent(room, 1))
            roomAccountDataStore.save(roomAccountDataEvent(room, 2))

            fun roomUser(roomId: RoomId, i: Int) =
                RoomUser(roomId, UserId("user$i", "server"), "$i", stateEvent(roomId, i))
            roomUserStore.update(UserId("1"), room) { roomUser(room, 1) }
            roomUserStore.update(UserId("2"), room) { roomUser(room, 2) }

            roomStore.getAll().first { it.size == 1 }

            cut.forgetRoom(room)

            roomStore.get(room).first() shouldBe null

            roomTimelineStore.get(EventId("1"), room).first() shouldBe null
            roomTimelineStore.get(EventId("2"), room).first() shouldBe null

            roomTimelineStore.getRelations(EventId("1"), room, RelationType.Replace).first() shouldBe null
            roomTimelineStore.getRelations(EventId("2"), room, RelationType.Replace).first() shouldBe null

            roomStateStore.getByStateKey<MemberEventContent>(room, "1").first() shouldBe null
            roomStateStore.getByStateKey<MemberEventContent>(room, "2").first() shouldBe null

            roomAccountDataStore.get<FullyReadEventContent>(room, "1").first() shouldBe null
            roomAccountDataStore.get<FullyReadEventContent>(room, "2").first() shouldBe null

            roomUserStore.get(UserId("1"), room).first() shouldBe null
            roomUserStore.get(UserId("2"), room).first() shouldBe null
        }
        should("not forget rooms when membershipt is not leave") {
            roomStore.update(room) { simpleRoom.copy(room) }

            roomStore.getAll().first { it.size == 1 }

            cut.forgetRoom(room)

            roomStore.get(room).first() shouldNotBe null
        }
    }
})