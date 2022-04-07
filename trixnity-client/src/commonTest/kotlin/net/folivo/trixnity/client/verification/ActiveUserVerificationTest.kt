package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.OlmEventServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.mocks.UserServiceMock
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.verification.ActiveVerificationState.AcceptedByOtherDevice
import net.folivo.trixnity.client.verification.ActiveVerificationState.Undefined
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.MessageEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.MismatchedSas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.User
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.MegolmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
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

    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var api: MatrixClientServerApiClient
    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var olmEventService: OlmEventServiceMock
    lateinit var room: RoomServiceMock
    lateinit var store: Store
    lateinit var storeScope: CoroutineScope

    lateinit var cut: ActiveUserVerification

    beforeTest {
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        api = newApi
        olmEventService = OlmEventServiceMock()
        room = RoomServiceMock()
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
    }
    afterTest {
        storeScope.cancel()
    }

    fun createCut(timestamp: Instant = Clock.System.now()) {
        cut = ActiveUserVerification(
            request = VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
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
            olmEvent = olmEventService,
            store = store,
            user = UserServiceMock(),
            room = room,
            keyTrust = KeyTrustServiceMock(),
        )
    }

    val requestTimelineEvent = TimelineEvent(
        event = MessageEvent(
            VerificationRequestMessageEventContent("from", alice, setOf()),
            EventId("e"),
            bob,
            roomId,
            1
        ),
        roomId = roomId,
        eventId = EventId("e"),
        previousEventId = null,
        nextEventId = null,
        gap = null
    )
    should("handle verification step") {
        val cancelEvent = VerificationCancelEventContent(User, "u", relatesTo, null)

        room.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        room.returnGetTimelineEvents = flowOf(
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
                    MegolmEncryptedEventContent("cipher", Curve25519Key(null, ""), bobDevice, "session"),
                    EventId("$2"), bob, roomId, 1234
                ),
                roomId = roomId, eventId = event,
                previousEventId = null, nextEventId = null, gap = null
            )
        )
        room.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        room.returnGetTimelineEvents = flowOf(
            MutableStateFlow( // ignore event, that is no VerificationStep
                TimelineEvent(
                    event = MessageEvent(
                        MegolmEncryptedEventContent("cipher", Curve25519Key(null, ""), bobDevice, "session"),
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
                        MegolmEncryptedEventContent("cipher", Curve25519Key(null, ""), bobDevice, "session"),
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
                        MegolmEncryptedEventContent("cipher", Curve25519Key(null, ""), bobDevice, "session"),
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
                MegolmEncryptedEventContent("cipher", Curve25519Key(null, ""), bobDevice, "session"),
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
        room.returnGetTimelineEvent = MutableStateFlow(null)
        store.room.update(roomId) { simpleRoom.copy(encryptionAlgorithm = Megolm, membersLoaded = true) }
        store.roomState.update(StateEvent(EncryptionEventContent(), EventId("e"), bob, roomId, 1, stateKey = ""))
        var sendMessageCalled = false
        val encrypted = MegolmEncryptedEventContent("", Curve25519Key(null, ""), "", "")
        apiConfig.endpoints {
            matrixJsonEndpoint(json, mappings, SendMessageEvent(roomId, "m.room.encrypted", ""), skipUrlCheck = true) {
                it shouldBe encrypted
                sendMessageCalled = true
                SendEventResponse(EventId("event"))
            }
        }
        olmEventService.returnEncryptMegolm = { encrypted }
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        sendMessageCalled shouldBe true
    }
    should("send verification step and use unencrypted when encrypt failed") {
        room.returnGetTimelineEvent = MutableStateFlow(null)
        store.room.update(roomId) { simpleRoom.copy(encryptionAlgorithm = Megolm, membersLoaded = true) }
        store.roomState.update(StateEvent(EncryptionEventContent(), EventId("e"), bob, roomId, 1, stateKey = ""))
        var sendMessageCalled = false
        apiConfig.endpoints {
            matrixJsonEndpoint(
                json,
                mappings,
                SendMessageEvent(roomId, "m.key.verification.cancel", ""),
                skipUrlCheck = true
            ) {
                it shouldBe VerificationCancelEventContent(User, "user cancelled verification", relatesTo, null)
                sendMessageCalled = true
                SendEventResponse(EventId("event"))
            }
        }
        olmEventService.returnEncryptMegolm = { throw OlmLibraryException(message = "hu") }
        createCut()
        cut.startLifecycle(this)
        cut.cancel()
        sendMessageCalled shouldBe true
    }
    should("stop lifecycle, when cancelled") {
        room.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        room.returnGetTimelineEvents = flowOf(
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
        room.returnGetTimelineEvent = MutableStateFlow(null)
        createCut(Clock.System.now() - 9.9.minutes)
        cut.startLifecycle(this)
    }
    should("set state to ${AcceptedByOtherDevice::class.simpleName} when request accepted by other device") {
        room.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        room.returnGetTimelineEvents = flowOf(
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
            )
        )
        cut = ActiveUserVerification(
            request = VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
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
            olmEvent = olmEventService,
            store = store,
            user = UserServiceMock(),
            room = room,
            keyTrust = KeyTrustServiceMock(),
        )
        cut.startLifecycle(this)
        cut.state.first { it == AcceptedByOtherDevice } shouldBe AcceptedByOtherDevice
        cut.cancel()
    }
    should("set state to ${Undefined::class.simpleName} when request accepted by own device, but state does not match (e.g. on restart)") {
        room.returnGetTimelineEvent = MutableStateFlow(requestTimelineEvent)
        room.returnGetTimelineEvents = flowOf(
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
            )
        )
        cut = ActiveUserVerification(
            request = VerificationRequestMessageEventContent(aliceDevice, bob, setOf(Sas)),
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
            olmEvent = olmEventService,
            store = store,
            user = UserServiceMock(),
            room = room,
            keyTrust = KeyTrustServiceMock(),
        )
        cut.startLifecycle(this)
        cut.state.first { it == Undefined } shouldBe Undefined
        cut.cancel()
    }
})