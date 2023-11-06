package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.ForwardedRoomKeyEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OutgoingRoomKeyRequestEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val room = RoomId("room", "server")
    val sessionId = "sessionId"
    val senderKey = Key.Curve25519Key(null, "sender")
    val senderSigningKey = Key.Ed25519Key(null, "senderSigning")
    val forwardingSenderKey = Key.Curve25519Key(null, "forwardingSenderKey")
    lateinit var scope: CoroutineScope
    lateinit var accountStore: AccountStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var keyStore: KeyStore
    lateinit var apiConfig: PortableMockEngineConfig


    lateinit var cut: OutgoingRoomKeyRequestEventHandlerImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        accountStore = getInMemoryAccountStore(scope).apply { updateAccount { it.copy(olmPickleKey = "") } }
        olmCryptoStore = getInMemoryOlmStore(scope)
        keyStore = getInMemoryKeyStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = OutgoingRoomKeyRequestEventHandlerImpl(
            UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            OlmDecrypterMock(),
            accountStore,
            keyStore,
            olmCryptoStore,
            CurrentSyncState(MutableStateFlow(SyncState.RUNNING)),
        )
    }

    afterTest {
        scope.cancel()
    }

    suspend fun sessionKeys(withIndexes: Int): List<String> =
        freeAfter(OlmOutboundGroupSession.create()) { outboundSession ->
            buildList {
                freeAfter(OlmInboundGroupSession.create(outboundSession.sessionKey)) { inboundSession ->
                    add(inboundSession.export(0))
                    for (i in 1..withIndexes) {
                        inboundSession.decrypt(
                            outboundSession.encrypt("bla")
                        )
                        add(inboundSession.export(i.toLong()))
                    }
                }
            }
        }

    suspend fun sessionKey() = sessionKeys(0).first()
    suspend fun pickleToInbound(sessionKey: String) =
        freeAfter(OlmInboundGroupSession.import(sessionKey)) { it.pickle("") }

    val encryptedEvent = ToDeviceEvent(
        EncryptedEventContent.OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = forwardingSenderKey,
        ), bob
    )

    suspend fun setRequest(receiverDeviceIds: Set<String>) {
        keyStore.addRoomKeyRequest(
            StoredRoomKeyRequest(
                RoomKeyRequestEventContent(
                    KeyRequestAction.REQUEST,
                    aliceDevice,
                    "requestId",
                    RoomKeyRequestEventContent.RequestedKeyInfo(
                        room,
                        sessionId,
                        EncryptionAlgorithm.Megolm,
                    )
                ), receiverDeviceIds, Clock.System.now()
            )
        )
        keyStore.allRoomKeyRequests.first { it.size == 1 }
    }
    context(OutgoingRoomKeyRequestEventHandlerImpl::handleOutgoingKeyRequestAnswer.name) {
        val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")
        suspend fun setDeviceKeys(trusted: Boolean) {
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                        KeySignatureTrustLevel.CrossSigned(trusted)
                    )
                )
            }
        }

        suspend fun forwardedRoomKeyEvent() =
            ForwardedRoomKeyEventContent(
                roomId = room,
                senderKey = senderKey,
                sessionId = sessionId,
                sessionKey = sessionKey(),
                senderClaimedKey = senderSigningKey,
                forwardingKeyChain = listOf(),
                algorithm = EncryptionAlgorithm.Megolm,
            )

        should("save new room key") {
            setDeviceKeys(true)
            val forwardedRoomKeyEvent = forwardedRoomKeyEvent()
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent,
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )

            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe
                    StoredInboundMegolmSession(
                        senderKey = forwardedRoomKeyEvent.senderKey,
                        senderSigningKey = forwardedRoomKeyEvent.senderClaimedKey,
                        sessionId = forwardedRoomKeyEvent.sessionId,
                        roomId = forwardedRoomKeyEvent.roomId,
                        firstKnownIndex = 0,
                        hasBeenBackedUp = false,
                        isTrusted = false,
                        forwardingCurve25519KeyChain = listOf(forwardingSenderKey),
                        pickled = pickleToInbound(forwardedRoomKeyEvent.sessionKey),
                    )
        }
        should("save new room key and cancel other requests") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.room_key_request", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice, "OTHER_DEVICE"))
            val forwardedRoomKeyEvent = forwardedRoomKeyEvent()
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent,
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )

            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe
                    StoredInboundMegolmSession(
                        senderKey = forwardedRoomKeyEvent.senderKey,
                        senderSigningKey = forwardedRoomKeyEvent.senderClaimedKey,
                        sessionId = forwardedRoomKeyEvent.sessionId,
                        roomId = forwardedRoomKeyEvent.roomId,
                        firstKnownIndex = 0,
                        hasBeenBackedUp = false,
                        isTrusted = false,
                        forwardingCurve25519KeyChain = listOf(forwardingSenderKey),
                        pickled = pickleToInbound(forwardedRoomKeyEvent.sessionKey),
                    )

            sendToDeviceEvents?.get(alice)?.get("OTHER_DEVICE") shouldBe RoomKeyRequestEventContent(
                KeyRequestAction.REQUEST_CANCELLATION,
                aliceDevice,
                "requestId",
                null
            )
            keyStore.allRoomKeyRequests.first { it.isEmpty() }
        }
        should("ignore room key with lesser index") {
            setDeviceKeys(true)
            val forwardedRoomKeyEvent = forwardedRoomKeyEvent()
            val existingSession = StoredInboundMegolmSession(
                senderKey = forwardedRoomKeyEvent.senderKey,
                senderSigningKey = forwardedRoomKeyEvent.senderClaimedKey,
                sessionId = forwardedRoomKeyEvent.sessionId,
                roomId = forwardedRoomKeyEvent.roomId,
                firstKnownIndex = 0,
                hasBeenBackedUp = false,
                isTrusted = false,
                forwardingCurve25519KeyChain = listOf(forwardingSenderKey),
                pickled = pickleToInbound(sessionKeys(1).last()),
            )
            olmCryptoStore.updateInboundMegolmSession(sessionId, room) { existingSession }

            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent,
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )

            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe existingSession
        }
        should("ignore when session key cannot be imported") {
            setDeviceKeys(true)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent().copy(sessionKey = "dino"),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )
            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe null
        }
        should("ignore, when sender device id cannot be found") {
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent(),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )
            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe null
        }
        should("ignore when sender is not trusted") {
            setDeviceKeys(false)
            cut.handleOutgoingKeyRequestAnswer(
                DecryptedOlmEventContainer(
                    encryptedEvent,
                    DecryptedOlmEvent(
                        forwardedRoomKeyEvent(),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    ),
                )
            )
            olmCryptoStore.getInboundMegolmSession(sessionId, room).first() shouldBe null
        }
    }
    context(OutgoingRoomKeyRequestEventHandlerImpl::cancelOldOutgoingKeyRequests.name) {
        should("only remove old requests and send cancel") {
            var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.room_key_request", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }
            val request1 = StoredRoomKeyRequest(
                RoomKeyRequestEventContent(
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId1",
                    null // not relevant for the test
                ), setOf(), Clock.System.now()
            )
            val request2 = StoredRoomKeyRequest(
                RoomKeyRequestEventContent(
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId2",
                    RoomKeyRequestEventContent.RequestedKeyInfo(
                        room,
                        sessionId,
                        EncryptionAlgorithm.Megolm,
                    )
                ), setOf(aliceDevice), (Clock.System.now() - 1.days - 1.seconds)
            )
            keyStore.addRoomKeyRequest(request1)
            keyStore.addRoomKeyRequest(request2)
            keyStore.allRoomKeyRequests.first { it.size == 2 }

            cut.cancelOldOutgoingKeyRequests()

            keyStore.allRoomKeyRequests.first { it.size == 1 } shouldBe setOf(request1)
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe
                    RoomKeyRequestEventContent(
                        KeyRequestAction.REQUEST_CANCELLATION, // <- important!
                        "OWN_ALICE_DEVICE",
                        "requestId2",
                        null // <- important!
                    )
        }
    }
    context(OutgoingRoomKeyRequestEventHandler::requestRoomKeys.name) {
        val aliceDevice2 = "ALICEDEVICE_2"
        val aliceDevice3 = "ALICEDEVICE_3"
        var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null
        beforeTest {
            sendToDeviceEvents = null
            apiConfig.endpoints {
                matrixJsonEndpoint(
                    SendToDevice("m.room_key_request", "*"),
                ) {
                    sendToDeviceEvents = it.messages
                }
            }

            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                        KeySignatureTrustLevel.CrossSigned(true)
                    ),
                    aliceDevice2 to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice2, setOf(), keysOf()), mapOf()),
                        KeySignatureTrustLevel.Valid(true)
                    ),
                    aliceDevice3 to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice3, setOf(), keysOf()), mapOf()),
                        KeySignatureTrustLevel.Valid(false)
                    )
                )
            }
        }
        should("send requests to verified devices") {
            val result = async { cut.requestRoomKeys(room, sessionId) }
            val storedRequest = keyStore.allRoomKeyRequests.first { it.isNotEmpty() }.firstOrNull().shouldNotBeNull()
            storedRequest.receiverDeviceIds shouldBe setOf(aliceDevice2)
            storedRequest.createdAt.shouldBeLessThanOrEqualTo(Clock.System.now())

            fun assertRequest(content: RoomKeyRequestEventContent) =
                assertSoftly(content) {
                    action shouldBe KeyRequestAction.REQUEST
                    requestingDeviceId shouldBe aliceDevice
                    requestId.shouldNotBeEmpty()
                    body shouldBe RoomKeyRequestEventContent.RequestedKeyInfo(
                        roomId = room,
                        sessionId = sessionId,
                        algorithm = EncryptionAlgorithm.Megolm,
                    )
                }

            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe null
            sendToDeviceEvents?.get(alice)?.get(aliceDevice3) shouldBe null
            val requestToAlice2 = sendToDeviceEvents?.get(alice)?.get(aliceDevice2)
                .shouldBeInstanceOf<RoomKeyRequestEventContent>()

            assertRequest(storedRequest.content)
            assertRequest(requestToAlice2)

            keyStore.deleteRoomKeyRequest(storedRequest.content.requestId)
            keyStore.allRoomKeyRequests.first { it.isEmpty() }
            result.await()
        }
        should("ignore when there is no verified device to send request to") {
            keyStore.updateDeviceKeys(alice) { null }
            cut.requestRoomKeys(room, sessionId)
            sendToDeviceEvents shouldBe null
            keyStore.allRoomKeyRequests.first { it.isEmpty() }
        }
        should("not create new request when there is already one") {
            setRequest(setOf(aliceDevice))
            val result = async { cut.requestRoomKeys(room, sessionId) }
            delay(500.milliseconds)
            result.isActive shouldBe true
            keyStore.deleteRoomKeyRequest("requestId")
            keyStore.allRoomKeyRequests.first { it.isEmpty() }
            sendToDeviceEvents shouldBe null
            result.await()
        }
    }
}