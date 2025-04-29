package net.folivo.trixnity.client.verification

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.verification.ActiveVerificationState.AcceptedByOtherDevice
import net.folivo.trixnity.client.verification.ActiveVerificationState.Undefined
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.MismatchedSas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveUserVerificationTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BBBBBB"
    private val event = EventId("$1")
    private val roomId = RoomId("room", "server")
    private val relatesTo = RelatesTo.Reference(event)

    private val json = createMatrixEventJson()
    private val roomServiceMock = RoomServiceMock()

    private val requestTimelineEvent = TimelineEvent(
        event = MessageEvent(
            VerificationRequest("from", alice, setOf()),
            EventId("e"),
            bob,
            roomId,
            1
        ),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )

    private val keyStore = getInMemoryKeyStore()

    @Test
    fun `handle verification step`() = runTest {
        val cancelEvent = VerificationCancelEventContent(User, "u", relatesTo, null)

        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow( // ignore event, that is no VerificationStep
                TimelineEvent(
                    event = MessageEvent(
                        RoomMessageEventContent.TextBased.Text("hi"),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore own event
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(MismatchedSas, "", relatesTo, null),
                        EventId("$2"), alice, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore event with other relates to
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(
                            MismatchedSas,
                            "",
                            RelatesTo.Reference(EventId("$0")),
                            null
                        ),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        cancelEvent,
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            )
        )
        val cut = createCut()
        cut.startLifecycle(this)
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }

    @Test
    fun `handle encrypted verification step`() = runTest {
        val cancelEvent = VerificationCancelEventContent(User, "u", relatesTo, null)
        val cancelFlow = MutableStateFlow(
            TimelineEvent(
                event = MessageEvent(
                    MegolmEncryptedMessageEventContent(
                        "cipher",
                        Curve25519KeyValue(""),
                        bobDevice,
                        "session"
                    ),
                    EventId("$2"), bob, roomId, 1234
                ),
                previousEventId = null, nextEventId = null, gap = null
            )
        )
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow( // ignore event, that is no VerificationStep
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedMessageEventContent("cipher", Curve25519KeyValue(""), bobDevice, "session"),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    content = Result.success(RoomMessageEventContent.TextBased.Text("hi")),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore own event
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedMessageEventContent("cipher", Curve25519KeyValue(""), bobDevice, "session"),
                        EventId("$2"), alice, roomId, 1234
                    ),
                    content = Result.success(VerificationCancelEventContent(MismatchedSas, "", relatesTo, null)),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore event with other relates to
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedMessageEventContent("cipher", Curve25519KeyValue(""), bobDevice, "session"),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    content = Result.success(
                        VerificationCancelEventContent(
                            MismatchedSas,
                            "",
                            RelatesTo.Reference(EventId("$0")),
                            null
                        )
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            cancelFlow
        )
        val cut = createCut()
        cut.startLifecycle(this)
        delay(500)
        cancelFlow.value = TimelineEvent(
            event = MessageEvent(
                MegolmEncryptedMessageEventContent("cipher", Curve25519KeyValue(""), bobDevice, "session"),
                EventId("$2"), bob, roomId, 1234
            ),
            content = Result.success(cancelEvent),
            previousEventId = null, nextEventId = null, gap = null
        )
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }

    @Test
    fun `send verification step`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf()
        val cut = createCut()
        cut.startLifecycle(this)
        cut.cancel()
        roomServiceMock.sentMessages.value.shouldNotBeEmpty().first().second
            .shouldBeInstanceOf<VerificationCancelEventContent>()
    }

    @Test
    fun `stop lifecycle when cancelled`() = runTest {
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(User, "r", relatesTo, null),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            )
        )
        val cut = createCut()
        cut.startLifecycle(this)
    }

    @Test
    fun `stop lifecycle when timed out`() = runTest {
        roomServiceMock.returnGetTimelineEvent = flowOf()
        val cut = createCut(testClock.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }

    @Test
    fun `set state to AcceptedByOtherDevice when request accepted by other device`() = runTest {
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        VerificationReadyEventContent(
                            fromDevice = "OTHER_DEVICE",
                            methods = setOf(),
                            relatesTo, null
                        ),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            )
        )
        val cut = ActiveUserVerificationImpl(
            request = VerificationRequest(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = currentTime,
            ownUserId = bob,
            ownDeviceId = bobDevice,
            theirUserId = alice,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            json = json,
            keyStore = keyStore,
            room = roomServiceMock,
            keyTrust = KeyTrustServiceMock(),
            clock = testClock,
        )
        cut.startLifecycle(this)
        cut.state.first { it == AcceptedByOtherDevice } shouldBe AcceptedByOtherDevice
        cut.cancel()
    }

    @Test
    fun `set state to Undefined when request accepted by own device but state does not match`() = runTest {
        roomServiceMock.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        roomServiceMock.returnGetTimelineEvents = flowOf(
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        VerificationReadyEventContent(
                            fromDevice = bobDevice,
                            methods = setOf(),
                            relatesTo, null
                        ),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    previousEventId = null, nextEventId = null, gap = null
                )
            )
        )
        val cut = ActiveUserVerificationImpl(
            request = VerificationRequest(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = currentTime,
            ownUserId = bob,
            ownDeviceId = bobDevice,
            theirUserId = alice,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            json = json,
            keyStore = keyStore,
            room = roomServiceMock,
            keyTrust = KeyTrustServiceMock(),
            clock = testClock,
        )
        cut.startLifecycle(this)
        cut.state.first { it == Undefined } shouldBe Undefined
        cut.cancel()
    }

    private fun TestScope.createCut(timestamp: Instant = testClock.now()): ActiveUserVerificationImpl =
        ActiveUserVerificationImpl(
            request = VerificationRequest(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = true,
            requestEventId = event,
            requestTimestamp = timestamp.toEpochMilliseconds(),
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            json = json,
            keyStore = keyStore,
            room = roomServiceMock,
            keyTrust = KeyTrustServiceMock(),
            clock = testClock,
        )
}