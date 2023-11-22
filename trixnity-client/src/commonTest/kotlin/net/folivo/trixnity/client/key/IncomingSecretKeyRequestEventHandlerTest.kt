package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.OlmDecrypterMock
import net.folivo.trixnity.client.mocks.OlmEncryptionServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.model.users.SendToDevice
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.DecryptedOlmEvent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class IncomingSecretKeyRequestEventHandlerTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var olmEncryptionServiceMock: OlmEncryptionServiceMock
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: IncomingSecretKeyRequestEventHandler

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        olmEncryptionServiceMock = OlmEncryptionServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = IncomingSecretKeyRequestEventHandler(
            UserInfo(alice, aliceDevice, Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            api,
            OlmDecrypterMock(),
            olmEncryptionServiceMock,
            keyStore
        )
        cut.startInCoroutineScope(scope)
    }

    afterTest {
        scope.cancel()
    }

    val encryptedEvent = ToDeviceEvent(
        EncryptedEventContent.OlmEncryptedEventContent(
            ciphertext = mapOf(),
            senderKey = Key.Curve25519Key(null, "")
        ), bob
    )

    context(IncomingSecretKeyRequestEventHandler::handleEncryptedIncomingKeyRequests.name) {
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
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                        "secretUserSigningKey"
                    )
                )
            }
            olmEncryptionServiceMock.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
            }
        }
        should("ignore request from other user") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            bobDevice,
                            "requestId"
                        ),
                        bob, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents shouldBe null
        }
        should("add request on request") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents?.get(alice)?.get(aliceDevice) shouldNotBe null
        }
        should("remove request on request cancellation") {
            cut.handleEncryptedIncomingKeyRequests(
                DecryptedOlmEventContainer(
                    encryptedEvent, DecryptedOlmEvent(
                        SecretKeyRequestEventContent(
                            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
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
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            sendToDeviceEvents shouldBe null
        }
    }
    context(IncomingSecretKeyRequestEventHandler::processIncomingKeyRequests.name) {
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
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())),
                        "secretUserSigningKey"
                    )
                )
            }
            olmEncryptionServiceMock.returnEncryptOlm = {
                EncryptedEventContent.OlmEncryptedEventContent(
                    ciphertext = mapOf(),
                    senderKey = Key.Curve25519Key("", "")
                )
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
                            SecretKeyRequestEventContent(
                                SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
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
                            SecretKeyRequestEventContent(
                                SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
                sendToDeviceEvents shouldBe null
            }
        }
        notAnswerRequest(KeySignatureTrustLevel.Valid(false))
        notAnswerRequest(KeySignatureTrustLevel.CrossSigned(false))
        notAnswerRequest(KeySignatureTrustLevel.NotCrossSigned)
        notAnswerRequest(KeySignatureTrustLevel.Blocked)
        notAnswerRequest(KeySignatureTrustLevel.Invalid("reason"))
    }
}