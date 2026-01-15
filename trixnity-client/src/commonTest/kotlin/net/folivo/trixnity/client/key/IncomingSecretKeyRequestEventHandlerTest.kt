package net.folivo.trixnity.client.key

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.model.user.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
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