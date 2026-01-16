package de.connect2x.trixnity.client.key

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.OlmDecrypterMock
import de.connect2x.trixnity.client.mocks.OlmEncryptionServiceMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.clientserverapi.model.user.SendToDevice
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.DecryptedOlmEvent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.KeyRequestAction
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import de.connect2x.trixnity.core.model.keys.*
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

class IncomingSecretKeyRequestEventHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val bob = UserId("bob", "server")
    private val aliceDevice = "ALICEDEVICE"
    private val bobDevice = "BOBDEVICE"

    private val keyStore = getInMemoryKeyStore()

    private val olmEncryptionServiceMock = OlmEncryptionServiceMock()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = IncomingSecretKeyRequestEventHandler(
        UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        api,
        OlmDecrypterMock(),
        olmEncryptionServiceMock,
        keyStore
    ).apply {
        startInCoroutineScope(testScope.backgroundScope)
    }

    private val encryptedEvent = ToDeviceEvent(
        OlmEncryptedToDeviceEventContent(
            ciphertext = mapOf(),
            senderKey = Curve25519KeyValue("")
        ), bob
    )

    private var sendToDeviceEvents: Map<UserId, Map<String, ToDeviceEventContent>>? = null

    @Test
    fun `handleEncryptedIncomingKeyRequests » ignore request from other user`() = runTest {
        handleEncryptedIncomingKeyRequestsSetup()
        cut.handleEncryptedIncomingKeyRequests(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        bobDevice,
                        "requestId"
                    ),
                    bob, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.processIncomingKeyRequests()
        sendToDeviceEvents shouldBe null
    }

    @Test
    fun `handleEncryptedIncomingKeyRequests » add request on request`() = runTest {
        handleEncryptedIncomingKeyRequestsSetup()
        cut.handleEncryptedIncomingKeyRequests(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId"
                    ),
                    alice, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.processIncomingKeyRequests()
        sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
    }

    @Test
    fun `handleEncryptedIncomingKeyRequests » remove request on request cancellation`() = runTest {
        handleEncryptedIncomingKeyRequestsSetup()
        cut.handleEncryptedIncomingKeyRequests(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId"
                    ),
                    alice, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.handleEncryptedIncomingKeyRequests(
            DecryptedOlmEventContainer(
                encryptedEvent, DecryptedOlmEvent(
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST_CANCELLATION,
                        aliceDevice,
                        "requestId"
                    ),
                    alice, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.processIncomingKeyRequests()
        sendToDeviceEvents shouldBe null
    }

    @Test
    fun `processIncomingKeyRequests » answer request with trust level Valid true`() =
        answerRequest(KeySignatureTrustLevel.Valid(true))

    @Test
    fun `processIncomingKeyRequests » answer request with trust level CrossSigned true`() =
        answerRequest(KeySignatureTrustLevel.CrossSigned(true))

    @Test
    fun `processIncomingKeyRequests » not answer request with trust level Valid false`() =
        notAnswerRequest(KeySignatureTrustLevel.Valid(false))

    @Test
    fun `processIncomingKeyRequests » not answer request with trust level CrossSigned false`() =
        notAnswerRequest(KeySignatureTrustLevel.CrossSigned(false))

    @Test
    fun `processIncomingKeyRequests » not answer request with trust level NotCrossSigned`() =
        notAnswerRequest(KeySignatureTrustLevel.NotCrossSigned)

    @Test
    fun `processIncomingKeyRequests » not answer request with trust level Blocked`() =
        notAnswerRequest(KeySignatureTrustLevel.Blocked)

    @Test
    fun `processIncomingKeyRequests » not answer request with trust level Invalid reason`() =
        notAnswerRequest(KeySignatureTrustLevel.Invalid("reason"))

    private suspend fun handleEncryptedIncomingKeyRequestsSetup() {
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
        keyStore.updateSecrets {
            mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "secretUserSigningKey"
                )
            )
        }
        olmEncryptionServiceMock.returnEncryptOlm = Result.success(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Curve25519KeyValue("")
            )
        )
    }

    private suspend fun processIncomingKeyRequestsSetup() {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SendToDevice("m.room.encrypted", "*"),
            ) {
                sendToDeviceEvents = it.messages
            }
        }
        keyStore.updateSecrets {
            mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                    "secretUserSigningKey"
                )
            )
        }
        olmEncryptionServiceMock.returnEncryptOlm = Result.success(
            OlmEncryptedToDeviceEventContent(
                ciphertext = mapOf(),
                senderKey = Curve25519KeyValue("")
            )
        )
    }

    private fun answerRequest(returnedTrustLevel: KeySignatureTrustLevel) = runTest {
        processIncomingKeyRequestsSetup()
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
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId"
                    ),
                    alice, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.processIncomingKeyRequests()
        cut.processIncomingKeyRequests()
        sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
    }

    private fun notAnswerRequest(returnedTrustLevel: KeySignatureTrustLevel) = runTest {
        processIncomingKeyRequestsSetup()
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
                    SecretKeyRequestEventContent(
                        SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId"
                    ),
                    alice, keysOf(), null, alice, keysOf()
                )
            )
        )
        cut.processIncomingKeyRequests()
        cut.processIncomingKeyRequests()
        sendToDeviceEvents shouldBe null
    }

}