package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.verification.ActiveVerificationState.AcceptedByOtherDevice
import net.folivo.trixnity.client.verification.ActiveVerificationState.Undefined
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.MismatchedSas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.olm.OlmLibraryException
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

    val api = mockk<MatrixClientServerApiClient>(relaxed = true)
    val olm = mockk<OlmService>(relaxed = true)
    val room = mockk<RoomService>(relaxed = true)
    val store = mockk<Store>(relaxed = true)

    lateinit var cut: ActiveUserVerification

    beforeTest {
        coEvery { api.json } returns mockk()
    }
    afterTest {
        clearAllMocks()
    }

    fun createCut(timestamp: Instant = Clock.System.now()) {
        cut = ActiveUserVerification(
            request = RoomMessageEventContent.VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = true,
            requestEventId = event,
            requestTimestamp = timestamp.toEpochMilliseconds(),
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            api = api,
            olm = olm,
            store = store,
            user = mockk(relaxUnitFun = true),
            room = room,
            key = mockk(),
        )
    }

    should("handle verification step") {
        val cancelEvent = VerificationCancelEventContent(User, "u", relatesTo, null)
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(mockk())
        coEvery { room.getNextTimelineEvent(any(), any()) }.returnsMany(
            MutableStateFlow( // ignore event, that is no VerificationStep
                TimelineEvent(
                    event = MessageEvent(
                        TextMessageEventContent("hi"),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore own event
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(MismatchedSas, "", relatesTo, null),
                        EventId("$2"), alice, roomId, 1234
                    ),
                    roomId = roomId, eventId = event,
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
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        cancelEvent,
                        EventId("$2"), bob, roomId, 1234
                    ),
                    roomId = roomId, eventId = event,
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
                    MegolmEncryptedEventContent("cipher", mockk(), bobDevice, "session"),
                    EventId("$2"), bob, roomId, 1234
                ),
                roomId = roomId, eventId = event,
                previousEventId = null, nextEventId = null, gap = null
            )
        )
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(mockk())
        coEvery { room.getNextTimelineEvent(any(), any()) }.returnsMany(
            MutableStateFlow( // ignore event, that is no VerificationStep
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedEventContent("cipher", mockk(), bobDevice, "session"),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    content = Result.success(TextMessageEventContent("hi")),
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore own event
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedEventContent("cipher", mockk(), bobDevice, "session"),
                        EventId("$2"), alice, roomId, 1234
                    ),
                    content = Result.success(VerificationCancelEventContent(MismatchedSas, "", relatesTo, null)),
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            MutableStateFlow( // ignore event with other relates to
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedEventContent("cipher", mockk(), bobDevice, "session"),
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
                    roomId = roomId, eventId = event,
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
                MegolmEncryptedEventContent("cipher", mockk(), bobDevice, "session"),
                EventId("$2"), bob, roomId, 1234
            ),
            content = Result.success(cancelEvent),
            roomId = roomId, eventId = event,
            previousEventId = null, nextEventId = null, gap = null
        )
        val result = cut.state.first { it is ActiveVerificationState.Cancel }
        result shouldBe ActiveVerificationState.Cancel(cancelEvent, false)
    }
    should("send verification step and encrypt it") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(null)
        coEvery { store.room.get(roomId) } returns MutableStateFlow(
            simpleRoom.copy(encryptionAlgorithm = Megolm, membersLoaded = true)
        )
        coEvery { store.roomState.getByStateKey(roomId, "", EncryptionEventContent::class)?.content } returns mockk()
        coEvery { api.rooms.sendMessageEvent(any(), any()) } returns Result.success(EventId("$24"))
        val encrypted = mockk<MegolmEncryptedEventContent>()
        coEvery { olm.events.encryptMegolm(any(), any(), any()) } returns encrypted
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        coVerify {
            api.rooms.sendMessageEvent(roomId, encrypted, any(), any())
        }
    }
    should("send verification step and use unencrypted when encrypt failed") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(null)
        coEvery { store.room.get(roomId) } returns MutableStateFlow(
            simpleRoom.copy(encryptionAlgorithm = Megolm, membersLoaded = true)
        )
        coEvery { store.roomState.getByStateKey(roomId, "", EncryptionEventContent::class)?.content } returns mockk()
        coEvery { api.rooms.sendMessageEvent(any(), any()) } returns Result.success(EventId("$24"))
        coEvery { olm.events.encryptMegolm(any(), any(), any()) } throws OlmLibraryException(message = "hu")
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        coVerify {
            api.rooms.sendMessageEvent(
                roomId,
                VerificationCancelEventContent(User, "user cancelled verification", relatesTo, null),
                any(), any()
            )
        }
    }
    should("stop lifecycle, when cancelled") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(mockk())
        coEvery { room.getNextTimelineEvent(any(), any()) }.returns(
            MutableStateFlow(
                TimelineEvent(
                    event = MessageEvent(
                        VerificationCancelEventContent(User, "r", relatesTo, null),
                        EventId("$2"), bob, roomId, 1234
                    ),
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            )
        )
        createCut()
        cut.startLifecycle(this)
    }
    should("stop lifecycle, when timed out") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(null)
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("set state to ${AcceptedByOtherDevice::class.simpleName} when request accepted by other device") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(mockk())
        coEvery { room.getNextTimelineEvent(any(), any()) }.returnsMany(
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
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            null
        )
        cut = ActiveUserVerification(
            request = RoomMessageEventContent.VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = Clock.System.now().toEpochMilliseconds(),
            ownUserId = bob,
            ownDeviceId = bobDevice,
            theirUserId = alice,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            api = api,
            olm = olm,
            store = store,
            user = mockk(relaxUnitFun = true),
            room = room,
            key = mockk(),
        )
        cut.startLifecycle(this)
        cut.state.first { it == AcceptedByOtherDevice } shouldBe AcceptedByOtherDevice
        cut.cancel()
    }
    should("set state to ${Undefined::class.simpleName} when request accepted by own device, but state does not match (e.g. on restart)") {
        coEvery { room.getTimelineEvent(event, roomId, any()) } returns MutableStateFlow(mockk())
        coEvery { room.getNextTimelineEvent(any(), any()) }.returnsMany(
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
                    roomId = roomId, eventId = event,
                    previousEventId = null, nextEventId = null, gap = null
                )
            ),
            null
        )
        cut = ActiveUserVerification(
            request = RoomMessageEventContent.VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
            requestIsFromOurOwn = false,
            requestEventId = event,
            requestTimestamp = Clock.System.now().toEpochMilliseconds(),
            ownUserId = bob,
            ownDeviceId = bobDevice,
            theirUserId = alice,
            theirInitialDeviceId = null,
            roomId = roomId,
            supportedMethods = setOf(Sas),
            api = api,
            olm = olm,
            store = store,
            user = mockk(relaxUnitFun = true),
            room = room,
            key = mockk(),
        )
        cut.startLifecycle(this)
        cut.state.first { it == Undefined } shouldBe Undefined
        cut.cancel()
    }
})