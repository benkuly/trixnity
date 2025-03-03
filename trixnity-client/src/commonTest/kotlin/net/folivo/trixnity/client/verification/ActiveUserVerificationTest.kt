package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.store.KeyStore
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
import kotlin.time.Duration.Companion.minutes

class ActiveUserVerificationTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"
    val event = EventId("$1")
    val roomId = RoomId("room", "server")
    val relatesTo = RelatesTo.Reference(event)

    val json = createMatrixEventJson()
    lateinit var roomServiceMock: RoomServiceMock
    lateinit var keyStore: KeyStore
    lateinit var scope: CoroutineScope

    lateinit var cut: ActiveUserVerificationImpl

    beforeTest {
        roomServiceMock = RoomServiceMock()
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
    }
    afterTest {
        scope.cancel()
    }

    fun createCut(timestamp: Instant = Clock.System.now()) {
        cut = ActiveUserVerificationImpl(
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
            clock = Clock.System,
        )
    }

    val requestTimelineEvent = TimelineEvent(
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
    should("handle verification step") {
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
        createCut()
        cut.startLifecycle(this)
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("handle encrypted verification step") {
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
        createCut()
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
    should("send verification step") {
        roomServiceMock.returnGetTimelineEvent = flowOf()
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        roomServiceMock.sentMessages.value.shouldNotBeEmpty().first().second
            .shouldBeInstanceOf<VerificationCancelEventContent>()
    }
    should("stop lifecycle, when cancelled") {
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
        createCut()
        cut.startLifecycle(this)
    }
    should("stop lifecycle, when timed out") {
        roomServiceMock.returnGetTimelineEvent = flowOf()
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("set state to ${AcceptedByOtherDevice::class.simpleName} when request accepted by other device") {
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
        cut = ActiveUserVerificationImpl(
            request = VerificationRequest(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = Clock.System.now().toEpochMilliseconds(),
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
            clock = Clock.System,
        )
        cut.startLifecycle(this)
        cut.state.first { it == AcceptedByOtherDevice } shouldBe AcceptedByOtherDevice
        cut.cancel()
    }
    should("set state to ${Undefined::class.simpleName} when request accepted by own device, but state does not match (e.g. on restart)") {
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
        cut = ActiveUserVerificationImpl(
            request = VerificationRequest(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = Clock.System.now().toEpochMilliseconds(),
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
            clock = Clock.System,
        )
        cut.startLifecycle(this)
        cut.state.first { it == Undefined } shouldBe Undefined
        cut.cancel()
    }
})