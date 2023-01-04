package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
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
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
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
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
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
    lateinit var possiblyEncryptEventMock: PossiblyEncryptEventMock
    lateinit var roomServiceMock: RoomServiceMock
    lateinit var keyServiceMock: KeyServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    lateinit var keySecretServiceMock: KeySecretServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: VerificationServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        possiblyEncryptEventMock = PossiblyEncryptEventMock()
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
            UserInfo(aliceUserId, aliceDeviceId, Key.Ed25519Key(null, ""), Curve25519Key(null, "")),
            api,
            possiblyEncryptEventMock,
            keyStore, globalAccountDataStore,
            olmDecrypterMock,
            olmEncryptionServiceMock,
            roomServiceMock, keyServiceMock, keyTrustServiceMock, keySecretServiceMock,
            CurrentSyncState(currentSyncState)
        )
        cut.startInCoroutineScope(scope)
    }
    afterTest {
        scope.cancel()
    }

    context("init") {
        context("handleVerificationRequestEvents") {
            should("ignore request, that is timed out") {
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce().getOrThrow()

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
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                }
                api.sync.startOnce().getOrThrow()
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
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
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
                    matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                    }
                }
                api.sync.startOnce().getOrThrow()

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
                val request = VerificationRequestEventContent(bobDeviceId, setOf(Sas), 1111, "transaction1")
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                cut.activeDeviceVerification.value shouldBe null
            }
            should("add device verification") {
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction1"
                )
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                activeDeviceVerification.state.value.shouldBeInstanceOf<TheirRequest>()
            }
            should("cancel second device verification") {
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
                    }
                }

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
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
                        DecryptedOlmEvent(request1, bobUserId, keysOf(), aliceUserId, keysOf())
                    )
                )
                olmDecrypterMock.eventSubscribers.first().first()(
                    DecryptedOlmEventContainer(
                        ToDeviceEvent(OlmEncryptedEventContent(mapOf(), Curve25519Key(null, "")), bobUserId),
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
                val request = VerificationRequestEventContent(
                    bobDeviceId,
                    setOf(Sas),
                    Clock.System.now().toEpochMilliseconds(),
                    "transaction"
                )
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
                        Sync.Response(
                            nextBatch = "nextBatch",
                            toDevice = Sync.Response.ToDevice(listOf(ToDeviceEvent(request, bobUserId)))
                        )
                    }
                    matrixJsonEndpoint(json, mappings, Sync(), skipUrlCheck = true) {
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
                api.sync.startOnce().getOrThrow()
                val activeDeviceVerification = cut.activeDeviceVerification.first { it != null }
                require(activeDeviceVerification != null)
                api.sync.startOnce().getOrThrow()
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
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                    ) {
                        SendEventResponse(EventId("$24event"))
                    }
                }
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
                roomServiceMock.returnGetTimelineEvent = MutableStateFlow(timelineEvent)
                roomServiceMock.returnGetTimelineEvents = flowOf(
                    MutableStateFlow(
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
    context(VerificationServiceImpl::createDeviceVerificationRequest.name) {
        should("send request to device and save locally") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    json, mappings,
                    SendToDevice("m.key.verification.request", "txn"),
                    skipUrlCheck = true
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            olmEncryptionServiceMock.returnEncryptOlm = { throw OlmLibraryException(message = "dino") }
            val createdVerification = cut.createDeviceVerificationRequest(bobUserId, setOf(bobDeviceId)).getOrThrow()
            val activeDeviceVerification = cut.activeDeviceVerification.filterNotNull().first()
            createdVerification shouldBe activeDeviceVerification
            assertSoftly(sendToDeviceEvents) {
                this?.shouldHaveSize(1)
                this?.get(bobUserId)?.get(bobDeviceId)
                    ?.shouldBeInstanceOf<VerificationRequestEventContent>()?.fromDevice shouldBe aliceDeviceId
            }
        }
    }
    context(VerificationServiceImpl::createUserVerificationRequest.name) {
        context("no direct room with user exists") {
            should("create room and send request into it") {
                var sendMessageEventCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(json, mappings, CreateRoom()) {
                        it.invite shouldBe setOf(bobUserId)
                        it.isDirect shouldBe true
                        CreateRoom.Response(roomId)
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                        skipUrlCheck = true
                    ) {
                        it shouldBe VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas))
                        sendMessageEventCalled = true
                        SendEventResponse(EventId("$1event"))
                    }
                }
                possiblyEncryptEventMock.returnEncryptMegolm = { throw OlmLibraryException(message = "dino") }
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                sendMessageEventCalled shouldBe true
            }
        }
        context("direct room with user exists") {
            should("send request to existing room") {
                var sendMessageEventCalled = false
                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        SendMessageEvent(roomId.e(), "m.room.message", "transaction1"),
                        skipUrlCheck = true
                    ) {
                        it shouldBe VerificationRequestMessageEventContent(aliceDeviceId, bobUserId, setOf(Sas))
                        sendMessageEventCalled = true
                        SendEventResponse(EventId("$1event"))
                    }
                }
                possiblyEncryptEventMock.returnEncryptMegolm = { throw OlmLibraryException(message = "dino") }
                globalAccountDataStore.update(
                    GlobalAccountDataEvent(DirectEventContent(mapOf(bobUserId to setOf(roomId))))
                )
                cut.createUserVerificationRequest(bobUserId).getOrThrow()
                sendMessageEventCalled shouldBe true
            }
        }
    }
    context(VerificationServiceImpl::getSelfVerificationMethods.name) {
        beforeTest {
            currentSyncState.value = SyncState.RUNNING
        }
        should("return ${SelfVerificationMethods.PreconditionsNotMet::class.simpleName}, when initial sync is still running") {
            currentSyncState.value = SyncState.INITIAL_SYNC
            keyStore.updateDeviceKeys(aliceUserId) {
                mapOf(
                    aliceDeviceId to StoredDeviceKeys(
                        Signed(DeviceKeys(aliceUserId, aliceDeviceId, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.NotCrossSigned()
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
                matrixJsonEndpoint(json, mappings, SendToDevice("", ""), skipUrlCheck = true) {
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
            globalAccountDataStore.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            globalAccountDataStore.update(GlobalAccountDataEvent(defaultKey, "KEY"))
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
            globalAccountDataStore.update(GlobalAccountDataEvent(DefaultSecretKeyEventContent("KEY")))
            globalAccountDataStore.update(GlobalAccountDataEvent(defaultKey, "KEY"))
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
                        KeySignatureTrustLevel.NotCrossSigned()
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
    context(VerificationServiceImpl::getActiveUserVerification.name) {
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