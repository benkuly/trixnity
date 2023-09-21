package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.getInMemoryAccountStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class IncomingRoomKeyRequestEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    val room = RoomId("room", "server")
    val senderKey = Key.Curve25519Key("sender", "sender")
    val senderSigningKey = Key.Ed25519Key("sender", "sender")
    val sessionId = "sessionId"
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var accountStore: AccountStore
    lateinit var keyStore: KeyStore
    lateinit var olmStore: OlmCryptoStore
    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: IncomingRoomKeyRequestEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        accountStore = getInMemoryAccountStore(scope).apply { updateAccount { it.copy(olmPickleKey = "") } }
        keyStore = getInMemoryKeyStore(scope)
        olmStore = getInMemoryOlmStore(scope)
        olmEncryptionServiceMock = OlmEncryptionServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json, mappings)
        apiConfig = newApiConfig
        cut = IncomingRoomKeyRequestEventHandler(
            UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            OlmDecrypterMock(),
            olmEncryptionServiceMock,
            accountStore,
            keyStore,
            olmStore,
        )
        cut.startInCoroutineScope(scope)
    }

    afterTest {
        scope.cancel()
    }

    val encryptedEvent = Event.ToDeviceEvent(
        EncryptedEventContent.OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = Key.Curve25519Key(null, "")
        ), bob
    )

    context(IncomingRoomKeyRequestEventHandler::handleEncryptedIncomingKeyRequests.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        Signed(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), null),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.room.encrypted", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            olmEncryptionServiceMock.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
            }
            olmStore.updateInboundMegolmSession(sessionId, room) {
                freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
                    freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                        StoredInboundMegolmSession(
                            senderKey = senderKey,
                            senderSigningKey = senderSigningKey,
                            sessionId = sessionId,
                            roomId = room,
                            firstKnownIndex = inboundSession.firstKnownIndex,
                            hasBeenBackedUp = true,
                            isTrusted = true,
                            forwardingCurve25519KeyChain = listOf(),
                            pickled = inboundSession.pickle("")
                        )
                    }
                }
            }
        }
        should("ignore request from other user") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        RoomKeyRequestEventContent(
                            KeyRequestAction.REQUEST,
                            bobDevice,
                            "requestId",
                            RoomKeyRequestEventContent.RequestedKeyInfo(
                                room,
                                sessionId,
                                EncryptionAlgorithm.Megolm,
                            )
                        ),
                        bob, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
            sendToDeviceEvents shouldBe null
        }
        should("add request on request") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        RoomKeyRequestEventContent(
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId",
                            RoomKeyRequestEventContent.RequestedKeyInfo(
                                room,
                                sessionId,
                                EncryptionAlgorithm.Megolm,
                            )
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
        }
        should("remove request on request cancellation") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        RoomKeyRequestEventContent(
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId",
                            RoomKeyRequestEventContent.RequestedKeyInfo(
                                room,
                                sessionId,
                                EncryptionAlgorithm.Megolm,
                            )
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        RoomKeyRequestEventContent(
                            KeyRequestAction.REQUEST_CANCELLATION,
                            aliceDevice,
                            "requestId",
                            null
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
            sendToDeviceEvents shouldBe null
        }
    }
    context(IncomingRoomKeyRequestEventHandler::processIncomingKeyRequests.name) {
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.room.encrypted", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            olmEncryptionServiceMock.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
            }
            olmStore.updateInboundMegolmSession(sessionId, room) {
                freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
                    freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                        StoredInboundMegolmSession(
                            senderKey = senderKey,
                            senderSigningKey = senderSigningKey,
                            sessionId = sessionId,
                            roomId = room,
                            firstKnownIndex = inboundSession.firstKnownIndex,
                            hasBeenBackedUp = true,
                            isTrusted = true,
                            forwardingCurve25519KeyChain = listOf(),
                            pickled = inboundSession.pickle("")
                        )
                    }
                }
            }
        }
        suspend fun ShouldSpecContainerScope.answerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("answer request with trust level $returnedTrustLevel") {
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            returnedTrustLevel
                        )
                    )
                }
                cut.handleEncryptedIncomingKeyRequests(
                    DecryptedOlmEventContainer(
                        encryptedEvent, DecryptedOlmEvent(
                            RoomKeyRequestEventContent(
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId",
                                RoomKeyRequestEventContent.RequestedKeyInfo(
                                    room,
                                    sessionId,
                                    EncryptionAlgorithm.Megolm,
                                )
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
                cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
                sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
            }
        }
        answerRequest(KeySignatureTrustLevel.Valid(true))
        answerRequest(KeySignatureTrustLevel.CrossSigned(true))
        suspend fun ShouldSpecContainerScope.notAnswerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("not answer request with trust level $returnedTrustLevel") {
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            returnedTrustLevel
                        )
                    )
                }
                cut.handleEncryptedIncomingKeyRequests(
                    DecryptedOlmEventContainer(
                        encryptedEvent, DecryptedOlmEvent(
                            RoomKeyRequestEventContent(
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId",
                                RoomKeyRequestEventContent.RequestedKeyInfo(
                                    room,
                                    sessionId,
                                    EncryptionAlgorithm.Megolm,
                                )
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
                cut.processIncomingKeyRequests(SyncProcessingData(Sync.Response(""), listOf()))
                sendToDeviceEvents shouldBe null
            }
        }
        notAnswerRequest(KeySignatureTrustLevel.Valid(false))
        notAnswerRequest(KeySignatureTrustLevel.CrossSigned(false))
        notAnswerRequest(KeySignatureTrustLevel.NotCrossSigned())
        notAnswerRequest(KeySignatureTrustLevel.Blocked())
        notAnswerRequest(KeySignatureTrustLevel.Invalid("reason"))
    }
}