package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.crypto.OlmEventService
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.client.verification.SelfVerificationMethod.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.core.EventSubscriber
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.ToDeviceEvent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmLibraryException
import kotlin.test.assertNotNull

class VerificationServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {

    timeout = 15_000
    val aliceUserId = UserId("alice", "server")
    val aliceDeviceId = "AAAAAA"
    val bobUserId = UserId("bob", "server")
    val bobDeviceId = "BBBBBB"
    val eventId = EventId("$1event")
    val roomId = RoomId("room", "server")
    val api = mockk<MatrixClientServerApiClient>(relaxed = true)
    lateinit var storeScope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val room = mockk<RoomService>()
    val user = mockk<UserService>(relaxUnitFun = true)
    val keyService = mockk<KeyService>()
    val json = createMatrixJson()
    lateinit var decryptedOlmEventFlow: MutableSharedFlow<OlmService.DecryptedOlmEvent>
    lateinit var cut: VerificationService

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope)
        decryptedOlmEventFlow = MutableSharedFlow()
        coEvery { olm.decryptedOlmEvents } returns decryptedOlmEventFlow
        coEvery { api.json } returns json
        cut = VerificationService(
            ownUserId = aliceUserId,
            ownDeviceId = aliceDeviceId,
            api = api,
            store = store,
            olmService = olm,
            roomService = room,
            userService = user,
            keyService = keyService,
        )
    }
    afterTest {
        storeScope.cancel()
        clearAllMocks()
    }
    context(VerificationService::start.name) {
        lateinit var eventHandlingCoroutineScope: CoroutineScope
        beforeTest {
            eventHandlingCoroutineScope = CoroutineScope(Dispatchers.Default)
        }
        afterTest {
            eventHandlingCoroutineScope.cancel()
        }
        context("handleVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                coEvery { api.sync.subscribe<VerificationRequestEventContent>(captureLambda()) }.coAnswers {
                    lambda<EventSubscriber<VerificationRequestEventContent>>().captured.invoke(
                        ToDeviceEvent(
                            request,
                            bobUserId
                        )
                    )
                }
                cut.start(eventHandlingCoroutineScope)
                val activeDeviceVerifications = cut.activeDeviceVerification.value
                activeDeviceVerifications shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                coEvery { api.sync.subscribe<VerificationRequestEventContent>(captureLambda()) }.coAnswers {
                    lambda<EventSubscriber<VerificationRequestEventContent>>().captured.invoke(
                        ToDeviceEvent(
                            request,
                            bobUserId
                        )
                    )
                }
                cut.start(eventHandlingCoroutineScope)
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second verification request") {
                val request1 = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                coEvery { api.sync.subscribe<VerificationRequestEventContent>(captureLambda()) }.coAnswers {
                    lambda<EventSubscriber<VerificationRequestEventContent>>().captured.invoke(
                        ToDeviceEvent(
                            request1,
                            bobUserId
                        )
                    )
                    lambda<EventSubscriber<VerificationRequestEventContent>>().captured.invoke(
                        ToDeviceEvent(
                            request2,
                            aliceUserId
                        )
                    )
                }
                val olmEvents: OlmEventService = mockk(relaxed = true)
                every { olm.events } returns olmEvents

                cut.start(eventHandlingCoroutineScope)

                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                coVerify {
                    olmEvents.encryptOlm(
                        match { it is VerificationCancelEventContent },
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("handleOlmDecryptedDeviceVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                cut.start(eventHandlingCoroutineScope)
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request, bobUserId, mockk(), mockk(), mockk())
                    )
                )
                cut.activeDeviceVerification.value shouldBe null
            }
            should("add device verification") {
                cut.start(eventHandlingCoroutineScope)
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request, bobUserId, mockk(), mockk(), mockk())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second device verification") {
                val olmEvents: OlmEventService = mockk(relaxed = true)
                every { olm.events } returns olmEvents

                cut.start(eventHandlingCoroutineScope)

                val request1 = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request1, bobUserId, mockk(), mockk(), mockk())
                    )
                )
                decryptedOlmEventFlow.emit(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(request2, aliceUserId, mockk(), mockk(), mockk())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                coVerify {
                    olmEvents.encryptOlm(
                        match { it is VerificationCancelEventContent },
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("startLifecycleOfActiveVerifications") {
            should("start all lifecycles of device verifications") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction"
                )
                coEvery { api.sync.subscribe<VerificationRequestEventContent>(captureLambda()) }.coAnswers {
                    lambda<EventSubscriber<VerificationRequestEventContent>>().captured.invoke(
                        ToDeviceEvent(request, bobUserId)
                    )
                }
                lateinit var verificationStepSubscriber: EventSubscriber<VerificationStep>
                coEvery { api.sync.subscribe<VerificationStep>(captureLambda()) }.coAnswers {
                    verificationStepSubscriber = lambda<EventSubscriber<VerificationStep>>().captured
                }
                cut.start(eventHandlingCoroutineScope)
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                verificationStepSubscriber.invoke(
                    ToDeviceEvent(VerificationCancelEventContent(Code.User, "user", null, "transaction"), bobUserId)
                )
                activeDeviceVerification.state.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", null, "transaction"),
                    false
                )
                cut.activeDeviceVerification.first { it == null } shouldBe null
            }
            should("start all lifecycles of user verifications") {
                cut.start(eventHandlingCoroutineScope)
                val nextEventId = EventId("$1nextEventId")
                coEvery {
                    api.rooms.sendMessageEvent(
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } returns Result.success(EventId("$24event"))
                val timelineEvent = TimelineEvent(
                    event = Event.MessageEvent(
                        VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                        eventId,
                        bobUserId,
                        roomId,
                        Clock.System.now().toEpochMilliseconds()
                    ),
                    eventId = eventId,
                    roomId = roomId,
                    previousEventId = null,
                    nextEventId = nextEventId,
                    gap = null
                )
                coEvery { room.getTimelineEvent(eventId, roomId, any()) } returns MutableStateFlow(timelineEvent)
                coEvery { room.getNextTimelineEvent(any(), any()) } returns MutableStateFlow(
                    TimelineEvent(
                        event = Event.MessageEvent(
                            VerificationCancelEventContent(
                                Code.User, "user",
                                transactionId = null,
                                relatesTo = RelatesTo.Reference(eventId)
                            ),
                            nextEventId,
                            bobUserId,
                            roomId,
                            Clock.System.now().toEpochMilliseconds()
                        ),
                        eventId = eventId,
                        roomId = roomId,
                        previousEventId = null,
                        nextEventId = nextEventId,
                        gap = null
                    )
                )
                val result = cut.getActiveUserVerification(timelineEvent)?.state
                assertNotNull(result)
                result.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", RelatesTo.Reference(eventId), null),
                    false
                )
            }
        }
    }
    context(VerificationService::createDeviceVerificationRequest.name) {
        should("send request to device and save locally") {
            coEvery { api.users.sendToDevice<ToDeviceEventContent>(any(), any(), any()) } returns Result.success(Unit)
            coEvery { olm.events.encryptOlm(any(), any(), any()) } throws OlmLibraryException(message = "dino")
            val createdVerification = cut.createDeviceVerificationRequest(bobUserId, bobDeviceId).getOrThrow()
            val activeDeviceVerification = cut.activeDeviceVerification.filterNotNull().first()
            createdVerification shouldBe activeDeviceVerification
            coVerify {
                api.users.sendToDevice<VerificationRequestEventContent>(withArg {
                    it shouldHaveSize 1
                    it[bobUserId]?.get(bobDeviceId)?.fromDevice shouldBe aliceDeviceId
                }, any(), any())
            }
        }
    }
    context(VerificationService::createUserVerificationRequest.name) {
        beforeTest {
            coEvery {
                api.rooms.sendMessageEvent(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns Result.success(EventId("$1event"))
            store.room.update(roomId) {
                Room(roomId, encryptionAlgorithm = EncryptionAlgorithm.Megolm, membersLoaded = true)
            }
            store.roomState.update(
                Event.StateEvent(
                    EncryptionEventContent(),
                    EventId("$24event"),
                    UserId("sender", "server"),
                    roomId,
                    1234,
                    stateKey = ""
                )
            )
        }
        context("no direct room with user exists") {
            should("create room and send request into it") {
                coEvery { olm.events.encryptMegolm(any(), any(), any()) } throws OlmLibraryException(message = "dino")
                coEvery { api.rooms.createRoom(invite = setOf(bobUserId), isDirect = true) } returns Result.success(
                    roomId
                )
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                coVerify {
                    api.rooms.sendMessageEvent(
                        roomId,
                        VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                        any()
                    )
                }
            }
        }
        context("direct room with user exists") {
            should("send request to existing room") {
                coEvery { olm.events.encryptMegolm(any(), any(), any()) } throws OlmLibraryException(message = "dino")
                store.globalAccountData.update(
                    GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
                )
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                coVerify {
                    api.rooms.sendMessageEvent(
                        roomId,
                        VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                        any()
                    )
                }
            }
        }
    }
    context(VerificationService::getSelfVerificationMethods.name) {
        lateinit var scope: CoroutineScope
        val syncState = MutableStateFlow(SyncApiClient.SyncState.RUNNING)
        beforeTest {
            scope = CoroutineScope(Dispatchers.Default)
            syncState.value = SyncApiClient.SyncState.RUNNING
            coEvery { api.sync.currentSyncState } returns syncState
        }
        afterTest { scope.cancel() }
        should("return null, when sync state is not running") {
            syncState.value = SyncApiClient.SyncState.INITIAL_SYNC
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned),
                    "DEV2" to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.CrossSigned(false)),
                    "DEV3" to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.Valid(false))
                )
            }
            val result = cut.getSelfVerificationMethods(scope)
            result.value shouldBe null
        }
        should("return null, when device keys not fetched yet") {
            val result = cut.getSelfVerificationMethods(scope)
            result.value shouldBe null
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned))
            }
            cut.getSelfVerificationMethods(scope).first { it?.isEmpty() == true }.shouldBeEmpty()
        }
        should("return empty set, when not ${KeySignatureTrustLevel.NotCrossSigned::class.simpleName}") {
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned))
            }
            cut.getSelfVerificationMethods(scope).value.shouldBeEmpty()
        }
        should("add ${CrossSignedDeviceVerification::class.simpleName}") {
            val spyCut = spyk(cut)
            val deviceVerification = mockk<ActiveDeviceVerification>()
            coEvery { spyCut.createDeviceVerificationRequest(any(), any()) } returns Result.success(deviceVerification)
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned),
                    "DEV2" to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.CrossSigned(false)),
                    "DEV3" to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.Valid(false))
                )
            }
            val result = spyCut.getSelfVerificationMethods(scope).value
            result?.size shouldBe 1
            val firstResult = result!!.first()
            firstResult.shouldBeInstanceOf<CrossSignedDeviceVerification>()
            firstResult.createDeviceVerification().getOrThrow() shouldBe deviceVerification
            coVerify { spyCut.createDeviceVerificationRequest(aliceUserId, "DEV2") }
        }
        should("don't add ${CrossSignedDeviceVerification::class.simpleName} when there are no cross signed devices") {
            val spyCut = spyk(cut)
            coEvery { spyCut.createDeviceVerificationRequest(any(), any()) } returns Result.success(mockk())
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned))
            }
            spyCut.getSelfVerificationMethods(scope).value?.size shouldBe 0
        }
        should("add ${AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = null,
            )
            store.globalAccountData.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            store.globalAccountData.update(GlobalAccountDataEvent(defaultKey, "KEY"))
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned))
            }
            cut.getSelfVerificationMethods(scope).value shouldBe setOf(
                AesHmacSha2RecoveryKey(keyService, "KEY", defaultKey)
            )
        }
        should("add ${AesHmacSha2RecoveryKey::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2("salt", 300_000),
            )
            store.globalAccountData.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            store.globalAccountData.update(GlobalAccountDataEvent(defaultKey, "KEY"))
            store.keys.updateDeviceKeys(aliceUserId) {
                mapOf(aliceDeviceId to StoredDeviceKeys(mockk(), KeySignatureTrustLevel.NotCrossSigned))
            }
            cut.getSelfVerificationMethods(scope).value shouldBe setOf(
                AesHmacSha2RecoveryKey(keyService, "KEY", defaultKey),
                AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keyService, "KEY", defaultKey)
            )
        }
    }
    context(VerificationService::getActiveUserVerification.name) {
        should("skip timed out verifications") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    1234
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result = cut.getActiveUserVerification(timelineEvent)
            result shouldBe null
        }
        should("return cached verification") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result1 = cut.getActiveUserVerification(timelineEvent)
            assertNotNull(result1)
            val result2 = cut.getActiveUserVerification(timelineEvent)
            result2 shouldBe result1
        }
        should("create verification from event") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            val result = cut.getActiveUserVerification(timelineEvent)
            val state = result?.state
            assertNotNull(state)
            state.value.shouldBeInstanceOf<TheirRequest>()
        }
        should("not create verification from own request event") {
            val timelineEvent = TimelineEvent(
                event = Event.MessageEvent(
                    VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas)),
                    eventId,
                    aliceUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                eventId = eventId,
                roomId = roomId,
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            cut.getActiveUserVerification(timelineEvent) shouldBe null
        }
    }
}