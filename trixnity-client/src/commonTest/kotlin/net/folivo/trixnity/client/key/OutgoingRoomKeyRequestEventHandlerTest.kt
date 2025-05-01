package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
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
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.olm.OlmInboundGroupSession
import net.folivo.trixnity.olm.OlmOutboundGroupSession
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OutgoingRoomKeyRequestEventHandlerTest : TrixnityBaseTest() {
    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDevice = "ALICEDEVICE"
    private val room = RoomId("room", "server")
    private val sessionId = "sessionId"
    private val senderKey = Key.Curve25519Key(null, "sender")
    private val senderSigningKey = Key.Ed25519Key(null, "senderSigning")
    private val forwardingSenderKey = Key.Curve25519Key(null, "forwardingSenderKey")

    private val accountStore = getInMemoryAccountStore { updateAccount { it?.copy(olmPickleKey = "") } }
    private val olmCryptoStore = getInMemoryOlmStore()
    private val keyStore = getInMemoryKeyStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = OutgoingRoomKeyRequestEventHandlerImpl(
        UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api,
        OlmDecrypterMock(),
        accountStore,
        keyStore,
        olmCryptoStore,
        CurrentSyncState(MutableStateFlow(SyncState.RUNNING)),
        testScope.testClock,
    )

    private val encryptedEvent = ToDeviceEvent(
        OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = forwardingSenderKey.value,
        ), bob
    )

    private val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")
    private val aliceDevice2 = "ALICEDEVICE_2"
    private val aliceDevice3 = "ALICEDEVICE_3"
    private var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null


    @Test
    fun `save new room key`() = runTest {
        clearOutdatedKeys { keyStore }
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
                    forwardingCurve25519KeyChain = listOf(forwardingSenderKey.value),
                    pickled = pickleToInbound(forwardedRoomKeyEvent.sessionKey),
                )
    }

    @Test
    fun `save new room key and cancel other requests`() = runTest {
        clearOutdatedKeys { keyStore }
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
                    forwardingCurve25519KeyChain = listOf(forwardingSenderKey.value),
                    pickled = pickleToInbound(forwardedRoomKeyEvent.sessionKey),
                )

        sendToDeviceEvents?.get(alice)?.get("OTHER_DEVICE") shouldBe RoomKeyRequestEventContent(
            KeyRequestAction.REQUEST_CANCELLATION,
            aliceDevice,
            "requestId",
            null
        )
        keyStore.getAllRoomKeyRequestsFlow().first { it.isEmpty() }
    }

    @Test
    fun `ignore room key with lesser index`() = runTest {
        clearOutdatedKeys { keyStore }
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
            forwardingCurve25519KeyChain = listOf(forwardingSenderKey.value),
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

    @Test
    fun `ignore when session key cannot be imported`() = runTest {
        clearOutdatedKeys { keyStore }
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

    @Test
    fun `ignore when sender device id cannot be found`() = runTest {
        clearOutdatedKeys { keyStore }
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

    @Test
    fun `ignore when sender is not trusted`() = runTest {
        clearOutdatedKeys { keyStore }
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


    @Test
    fun `cancelOldOutgoingKeyRequests » only remove old requests and send cancel`() = runTest {
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
            ), setOf(), testClock.now()
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
            ), setOf(aliceDevice), (testClock.now() - 1.days - 1.seconds)
        )
        keyStore.addRoomKeyRequest(request1)
        keyStore.addRoomKeyRequest(request2)
        keyStore.getAllRoomKeyRequestsFlow().first { it.size == 2 }

        cut.cancelOldOutgoingKeyRequests()

        keyStore.getAllRoomKeyRequestsFlow().first { it.size == 1 } shouldBe setOf(request1)
        sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldBe
                RoomKeyRequestEventContent(
                    KeyRequestAction.REQUEST_CANCELLATION, // <- important!
                    "OWN_ALICE_DEVICE",
                    "requestId2",
                    null // <- important!
                )
    }

    @Test
    fun `requestRoomKeys » send requests to verified devices`() = runTest {
        requestRoomKeysSetup()
        val result = async { cut.requestRoomKeys(room, sessionId) }
        val storedRequest =
            keyStore.getAllRoomKeyRequestsFlow().first { it.isNotEmpty() }.firstOrNull().shouldNotBeNull()
        storedRequest.receiverDeviceIds shouldBe setOf(aliceDevice2)
        storedRequest.createdAt.shouldBeLessThanOrEqualTo(testClock.now())

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
        keyStore.getAllRoomKeyRequestsFlow().first { it.isEmpty() }
        result.await()
    }

    @Test
    fun `requestRoomKeys » ignore when there is no verified device to send request to`() = runTest {
        requestRoomKeysSetup()
        keyStore.updateDeviceKeys(alice) { null }
        cut.requestRoomKeys(room, sessionId)
        sendToDeviceEvents shouldBe null
        keyStore.getAllRoomKeyRequestsFlow().first { it.isEmpty() }
    }

    @Test
    fun `requestRoomKeys » not create new request when there is already one`() = runTest {
        requestRoomKeysSetup()
        setRequest(setOf(aliceDevice))
        val result = async { cut.requestRoomKeys(room, sessionId) }
        delay(500.milliseconds)
        result.isActive shouldBe true
        keyStore.deleteRoomKeyRequest("requestId")
        keyStore.getAllRoomKeyRequestsFlow().first { it.isEmpty() }
        sendToDeviceEvents shouldBe null
        result.await()
    }

    private suspend fun TestScope.requestRoomKeysSetup() {
        clearOutdatedKeys { keyStore }
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

    private suspend fun sessionKeys(withIndexes: Int): List<String> =
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

    private suspend fun sessionKey() = sessionKeys(0).first()
    private suspend fun pickleToInbound(sessionKey: String) =
        freeAfter(OlmInboundGroupSession.import(sessionKey)) { it.pickle("") }

    private suspend fun TestScope.setRequest(receiverDeviceIds: Set<String>) {
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
                ), receiverDeviceIds, testClock.now()
            )
        )
        keyStore.getAllRoomKeyRequestsFlow().first { it.size == 1 }
    }

    private suspend fun setDeviceKeys(trusted: Boolean) {
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                    KeySignatureTrustLevel.CrossSigned(trusted)
                )
            )
        }
    }

    private suspend fun forwardedRoomKeyEvent() =
        ForwardedRoomKeyEventContent(
            roomId = room,
            senderKey = senderKey.value,
            sessionId = sessionId,
            sessionKey = sessionKey(),
            senderClaimedKey = senderSigningKey.value,
            forwardingKeyChain = listOf(),
            algorithm = EncryptionAlgorithm.Megolm,
        )
}