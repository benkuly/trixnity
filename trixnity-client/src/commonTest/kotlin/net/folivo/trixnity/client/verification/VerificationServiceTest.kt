package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.TheirRequest
import net.folivo.trixnity.client.verification.SelfVerificationMethod.*
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods.PreconditionsNotMet.Reason
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.startOnce
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.SendEventResponse
import net.folivo.trixnity.clientserverapi.model.rooms.SendMessageEvent
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import net.folivo.trixnity.olm.OlmLibraryException
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds


class VerificationServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {

    timeout = 30_000
    val aliceUserId = UserId("alice", "server")
    val aliceDeviceId = "AAAAAA"
    val bobUserId = UserId("bob", "server")
    val bobDeviceId = "BBBBBB"
    val eventId = EventId("$1event")
    val roomId = RoomId("room", "server")
    lateinit var apiConfig: PortableMockEngineConfig
    lateinit var api: MatrixClientServerApiClientImpl
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var olmDecrypterMock: OlmDecrypterMock
    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock
    lateinit var roomServiceMock: RoomServiceMock
    lateinit var keyServiceMock: KeyServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    lateinit var keySecretServiceMock: KeySecretServiceMock
    val json = createMatrixEventJson()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: VerificationServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        olmDecrypterMock = OlmDecrypterMock()
        olmEncryptionServiceMock = OlmEncryptionServiceMock()
        roomServiceMock = RoomServiceMock()
        keyServiceMock = KeyServiceMock()
        keyTrustServiceMock = KeyTrustServiceMock()
        keySecretServiceMock = KeySecretServiceMock()
        val (newApi, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        api = newApi
        cut = VerificationServiceImpl(
            userInfo = UserInfo(aliceUserId, aliceDeviceId, Key.Ed25519Key(null, ""), Curve25519Key(null, "")),
            api = api,
            keyStore = keyStore,
            globalAccountDataStore = globalAccountDataStore,
            olmDecrypter = olmDecrypterMock,
            olmEncryptionService = olmEncryptionServiceMock,
            roomService = roomServiceMock,
            keyService = keyServiceMock,
            keyTrustService = keyTrustServiceMock,
            keySecretService = keySecretServiceMock,
            currentSyncState = CurrentSyncState(currentSyncState),
            clock = Clock.System,
        )
        cut.startInCoroutineScope(scope)
    }
    afterTest {
        scope.cancel()
    }

    context("init") {
        context("handleVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestToDeviceEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync()) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce(
                    getBatchToken = { null },
                    setBatchToken = {},
                ).getOrThrow()

                val activeDeviceVerifications = cut.activeDeviceVerification.value
                activeDeviceVerifications shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestToDeviceEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync()) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce(
                    getBatchToken = { null },
                    setBatchToken = {},
                ).getOrThrow()
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second verification request") {
                val request1 = VerificationRequestToDeviceEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestToDeviceEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync()) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(
                                listOf(
                                    ToDeviceEvent(request1, bobUserId),
                                    ToDeviceEvent(request2, aliceUserId)
                                )
                            )
                        )
                    }
                    matrixJsonEndpoint(SendToDevice("m.key.verification.cancel", "*")) {
                    }
                }
                api.sync.startOnce(
                    getBatchToken = { null },
                    setBatchToken = {},
                ).getOrThrow()

                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                eventually(1.seconds) {
                    olmEncryptionServiceMock.encryptOlmCalled shouldBe Triple(
                        VerificationCancelEventContent(Code.User, "user cancelled verification", null, "transaction2"),
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("handleOlmDecryptedDeviceVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestToDeviceEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                cut.activeDeviceVerification.value shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestToDeviceEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second device verification") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(SendToDevice("m.key.verification.cancel", "*")) {
                    }
                }

                val request1 = VerificationRequestToDeviceEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                val request2 = VerificationRequestToDeviceEventContent(
                    aliceDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction2"
                )
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request1, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedToDeviceEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request2, aliceUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.theirDeviceId shouldBe bobDeviceId
                eventually(1.seconds) {
                    olmEncryptionServiceMock.encryptOlmCalled shouldBe Triple(
                        VerificationCancelEventContent(
                            Code.User,
                            "already have an active device verification",
                            null,
                            "transaction2"
                        ),
                        aliceUserId,
                        aliceDeviceId
                    )
                }
            }
        }
        context("startLifecycleOfActiveVerifications") {
            should("start all lifecycles of device verifications") {
                val request = VerificationRequestToDeviceEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(Sync(since = "token1")) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                    matrixJsonEndpoint(Sync(since = "token2")) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(
                                listOf(
                                    ToDeviceEvent(
                                        VerificationCancelEventContent(Code.User, "user", null, "transaction"),
                                        bobUserId
                                    )
                                )
                            )
                        )
                    }
                }
                api.sync.startOnce(
                    getBatchToken = { "token1" },
                    setBatchToken = {},
                ).getOrThrow()
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                api.sync.startOnce(
                    getBatchToken = { "token2" },
                    setBatchToken = {},
                ).getOrThrow()
                activeDeviceVerification.state.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", null, "transaction"),
                    false
                )
                cut.activeDeviceVerification.first { it == null } shouldBe null
            }
            should("start all lifecycles of user verifications") {
                val nextEventId = EventId("$1nextEventId")
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        SendMessageEvent(roomId, "m.room.message", "transaction1"),
                    ) {
                        SendEventResponse(EventId("$24event"))
                    }
                }
                val timelineEvent = TimelineEvent(
                    event = MessageEvent(
                        VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                        eventId,
                        bobUserId,
                        roomId,
                        Clock.System.now().toEpochMilliseconds()
                    ),
                    previousEventId = null,
                    nextEventId = nextEventId,
                    gap = null
                )
                roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
                roomServiceMock.returnGetTimelineEvents = flowOf(
                    MutableStateFlow(
                        TimelineEvent(
                            event = MessageEvent(
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
                            previousEventId = null,
                            nextEventId = nextEventId,
                            gap = null
                        )
                    )
                )
                val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)?.state
                assertNotNull(result)
                result.first { it is Cancel } shouldBe Cancel(
                    VerificationCancelEventContent(Code.User, "user", RelatesTo.Reference(eventId), null),
                    false
                )
            }
        }
    }
    context(VerificationServiceImpl::createDeviceVerificationRequest.name) {
        should("send request to device and save locally") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.key.verification.request", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            olmEncryptionServiceMock.returnEncryptOlm =
                Result.failure(OlmEncryptionService.EncryptOlmError.OlmLibraryError(OlmLibraryException("dino")))
            val createdVerification = cut.createDeviceVerificationRequest(bobUserId, setOf(bobDeviceId)).getOrThrow()
            val activeDeviceVerification = cut.activeDeviceVerification.filterNotNull().first()
            createdVerification shouldBe activeDeviceVerification
            assertSoftly(sendToDeviceEvents) {
                this?.shouldHaveSize(1)
                this?.get(bobUserId)?.get(bobDeviceId)
                    ?.shouldBeInstanceOf<VerificationRequestToDeviceEventContent>()?.fromDevice shouldBe aliceDeviceId
            }
        }
    }
    context(VerificationServiceImpl::createUserVerificationRequest.name) {
        context("no direct room with user exists") {
            should("create room and send request into it") {
                var createRoomCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(CreateRoom()) {
                        createRoomCalled = true
                        it.invite shouldBe setOf(bobUserId)
                        it.isDirect shouldBe true
                        CreateRoom.Response(roomId)
                    }
                }
                val result = async { cut.createUserVerificationRequest(bobUserId).getOrThrow() }
                val message = roomServiceMock.sentMessages.first { it.isNotEmpty() }.first().second
                roomServiceMock.outbox.value =
                    listOf(
                        flowOf(
                            RoomOutboxMessage(
                                transactionId = "1",
                                roomId = roomId,
                                content = message,
                                eventId = EventId("bla"),
                                createdAt = Clock.System.now(),
                            )
                        )
                    )

                result.await()
                createRoomCalled shouldBe true
            }
        }
        context("direct room with user exists") {
            should("send request to existing room") {
                globalAccountDataStore.save(
                    GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
                )
                val result = async { cut.createUserVerificationRequest(bobUserId).getOrThrow() }
                val message = roomServiceMock.sentMessages.first { it.isNotEmpty() }.first().second
                roomServiceMock.outbox.value =
                    listOf(
                        flowOf(
                            RoomOutboxMessage(
                                transactionId = "1",
                                roomId = roomId,
                                content = message,
                                eventId = EventId("bla"),
                                createdAt = Clock.System.now(),
                            )
                        )
                    )
                result.await()
            }
        }
    }
    context(VerificationServiceImpl::getSelfVerificationMethods.name) {
        clearOutdatedKeys { keyStore }
        beforeTest {
            currentSyncState.value = SyncState.RUNNING
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet::class.simpleName}, when initial sync is still running") {
            currentSyncState.value = SyncState.INITIAL_SYNC
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.SyncNotRunning))
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet::class.simpleName}, when device keys not fetched yet") {
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.DeviceKeysNotFetchedYet))
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet::class.simpleName}, when cross signing keys not fetched yet") {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.PreconditionsNotMet(setOf(Reason.CrossSigningKeysNotFetchedYet))
        }
        should("return ${SelfVerificationMethods.NoCrossSigningEnabled}, when cross signing keys are fetched, but empty") {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf()
            }
            val result = cut.getSelfVerificationMethods()
            result.first() shouldBe SelfVerificationMethods.NoCrossSigningEnabled
        }
        should("return ${SelfVerificationMethods.AlreadyCrossSigned} when already cross signed") {
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            cut.getSelfVerificationMethods().first() shouldBe SelfVerificationMethods.AlreadyCrossSigned
        }
        should("add ${CrossSignedDeviceVerification::class.simpleName}") {
            apiConfig.endpoints {
                matrixJsonEndpoint(SendToDevice("m.key.verification.request", "*")) {
                }
            }
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    ),
                    "DEV2" to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, "DEV2", setOf(), keysOf()), null),
                        KeySignatureTrustLevel.CrossSigned(false)
                    ),
                    "DEV3" to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, "DEV3", setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(false)
                    )
                )
            }
            val result = cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods
            result.size shouldBe 1
            val firstResult = result.first()
            firstResult.shouldBeInstanceOf<CrossSignedDeviceVerification>()
            firstResult.createDeviceVerification().getOrThrow().shouldBeInstanceOf<ActiveDeviceVerification>()
        }
        should("don't add ${CrossSignedDeviceVerification::class.simpleName} when there are no cross signed devices") {
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>()
                .methods.size shouldBe 0
        }
        should("add ${AesHmacSha2RecoveryKeyWithPbkdf2Passphrase::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = null,
            )
            globalAccountDataStore.save(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            globalAccountDataStore.save(GlobalAccountDataEvent(defaultKey, "KEY"))
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
                AesHmacSha2RecoveryKey(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey)
            )
        }
        should("add ${AesHmacSha2RecoveryKey::class.simpleName}") {
            val defaultKey = SecretKeyEventContent.AesHmacSha2Key(
                name = "default key",
                passphrase = SecretKeyEventContent.AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2("salt", 10_000),
            )
            globalAccountDataStore.save(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            globalAccountDataStore.save(GlobalAccountDataEvent(defaultKey, "KEY"))
            keyStore.updateCrossSigningKeys(aliceUserId) {
                setOf(
                    StoredCrossSigningKeys(
                        Signed(CrossSigningKeys(aliceUserId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned
                    )
                )
            }
            cut.getSelfVerificationMethods().first()
                .shouldBeInstanceOf<SelfVerificationMethods.CrossSigningEnabled>().methods shouldBe setOf(
                AesHmacSha2RecoveryKey(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey),
                AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(keySecretServiceMock, keyTrustServiceMock, "KEY", defaultKey)
            )
        }
    }
    context("getActiveUserVerification") {
        should("skip timed out verifications") {
            val timelineEvent = TimelineEvent(
                event = MessageEvent(
                    VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    1234
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
            val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
            result shouldBe null
        }
        should("return cached verification") {
            val timelineEvent = TimelineEvent(
                event = MessageEvent(
                    VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
            val result1 = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
            assertNotNull(result1)
            val result2 = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
            result2 shouldBe result1
        }
        should("create verification from event") {
            val timelineEvent = TimelineEvent(
                event = MessageEvent(
                    VerificationRequest(bobDeviceId, aliceUserId, setOf(Sas)),
                    eventId,
                    bobUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
            val result = cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)
            val state = result?.state
            assertNotNull(state)
            state.value.shouldBeInstanceOf<TheirRequest>()
        }
        should("not create verification from own request event") {
            val timelineEvent = TimelineEvent(
                event = MessageEvent(
                    VerificationRequest(aliceDeviceId, bobUserId, setOf(Sas)),
                    eventId,
                    aliceUserId,
                    roomId,
                    Clock.System.now().toEpochMilliseconds()
                ),
                previousEventId = null,
                nextEventId = null,
                gap = null
            )
            roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
            cut.getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId) shouldBe null
        }
    }
}