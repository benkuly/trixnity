package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.crypto.createAesHmacSha2MacFromKey
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.KeySecretServiceMock
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.OlmSignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.keys.SetCrossSigningKeys
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.random.Random

class KeyServiceCrossSigningTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 10_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var olmSign: OlmSignServiceMock
    lateinit var backup: KeyBackupServiceMock
    lateinit var trust: KeyTrustServiceMock
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncState.STOPPED)

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        olmSign = OlmSignServiceMock()
        backup = KeyBackupServiceMock()
        trust = KeyTrustServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyService(
            alice,
            aliceDevice,
            store,
            olmSign,
            api,
            currentSyncState,
            KeySecretServiceMock(),
            backup,
            trust,
            scope,
        )
        olmSign.returnVerify = VerifyResult.Valid
    }

    afterTest {
        scope.cancel()
    }

    context(KeyService::checkOwnAdvertisedMasterKeyAndVerifySelf.name) {
        val recoveryKey = Random.nextBytes(32)
        val iv = Random.nextBytes(16)
        val keyInfo = AesHmacSha2Key(
            iv = iv.encodeBase64(),
            mac = createAesHmacSha2MacFromKey(recoveryKey, iv)
        )
        val keyId = "keyId"
        val (masterSigningPrivateKey, masterSigningPublicKey) =
            freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
        val encryptedMasterSigningKey = MasterKeyEventContent(
            encryptSecret(recoveryKey, keyId, "m.cross_signing.master", masterSigningPrivateKey, json)
        )
        should("fail when master key cannot be found") {
            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).isFailure shouldBe true
        }
        should("fail when master key does not match") {
            store.globalAccountData.update(ClientEvent.GlobalAccountDataEvent(encryptedMasterSigningKey))
            val publicKey = Random.nextBytes(32).encodeUnpaddedBase64()
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.UserSigningKey), keysOf(
                                    Ed25519Key(publicKey, publicKey)
                                )
                            ), mapOf()
                        ), CrossSigned(true)
                    )
                )
            }

            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).isFailure shouldBe true
        }
        should("be success, when master key matches") {
            store.globalAccountData.update(ClientEvent.GlobalAccountDataEvent(encryptedMasterSigningKey))
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                    Ed25519Key(masterSigningPublicKey, masterSigningPublicKey)
                                )
                            ), mapOf()
                        ), Valid(false)
                    )
                )
            }
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(
                                alice, aliceDevice, setOf(),
                                keysOf(Ed25519Key(aliceDevice, "dev"))
                            ), mapOf()
                        ),
                        Valid(false)
                    )
                )
            }

            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).getOrThrow()

            trust.trustAndSignKeysCalled.value shouldBe
                    (setOf(
                        Ed25519Key(masterSigningPublicKey, masterSigningPublicKey),
                        Ed25519Key(aliceDevice, "dev")
                    ) to alice)
        }
    }
    context(KeyService::bootstrapCrossSigning.name) {
        context("successfull") {
            var secretKeyEventContentCalled = false
            var capturedPassphrase: AesHmacSha2Key.SecretStorageKeyPassphrase? = null
            var defaultSecretKeyEventContentCalled = false
            var masterKeyEventContentCalled = false
            var userSigningKeyEventContentCalled = false
            var selfSigningKeyEventContentCalled = false
            var setCrossSigningKeysCalled = false
            beforeTest {
                secretKeyEventContentCalled = false
                capturedPassphrase = null
                defaultSecretKeyEventContentCalled = false
                masterKeyEventContentCalled = false
                userSigningKeyEventContentCalled = false
                selfSigningKeyEventContentCalled = false
                setCrossSigningKeysCalled = false

                apiConfig.endpoints {
                    matrixJsonEndpoint(
                        json, mappings,
                        SetGlobalAccountData(alice.e(), "m.secret_storage.key."),
                        skipUrlCheck = true
                    ) {
                        it.shouldBeInstanceOf<AesHmacSha2Key>()
                        it.iv shouldNot beEmpty()
                        it.mac shouldNot beEmpty()
                        capturedPassphrase = it.passphrase
                        secretKeyEventContentCalled = true
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SetGlobalAccountData(alice.e(), "m.secret_storage.default_key")
                    ) {
                        it.shouldBeInstanceOf<DefaultSecretKeyEventContent>()
                        it.key.length shouldBeGreaterThan 10
                        defaultSecretKeyEventContentCalled = true
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SetGlobalAccountData(alice.e(), "m.cross_signing.master")
                    ) {
                        it.shouldBeInstanceOf<MasterKeyEventContent>()
                        val encrypted = it.encrypted.values.first()
                        encrypted.shouldBeInstanceOf<JsonObject>()
                        encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        masterKeyEventContentCalled = true
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SetGlobalAccountData(alice.e(), "m.cross_signing.user_signing")
                    ) {
                        it.shouldBeInstanceOf<UserSigningKeyEventContent>()
                        val encrypted = it.encrypted.values.first()
                        encrypted.shouldBeInstanceOf<JsonObject>()
                        encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        userSigningKeyEventContentCalled = true
                    }
                    matrixJsonEndpoint(
                        json, mappings,
                        SetGlobalAccountData(alice.e(), "m.cross_signing.self_signing")
                    ) {
                        it.shouldBeInstanceOf<SelfSigningKeyEventContent>()
                        val encrypted = it.encrypted.values.first()
                        encrypted.shouldBeInstanceOf<JsonObject>()
                        encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        selfSigningKeyEventContentCalled = true
                    }
                    matrixJsonEndpoint(json, mappings, SetCrossSigningKeys()) {
                        it.request.masterKey shouldNotBe null
                        it.request.selfSigningKey shouldNotBe null
                        it.request.userSigningKey shouldNotBe null
                        setCrossSigningKeysCalled = true
                        ResponseWithUIA.Success(Unit)
                    }
                }
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            SignedCrossSigningKeys(
                                CrossSigningKeys(
                                    alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                        Ed25519Key("A_MSK", "A_MSK")
                                    )
                                ), mapOf()
                            ), Valid(false)
                        )
                    )
                }
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, aliceDevice, setOf(),
                                    keysOf(Ed25519Key(aliceDevice, "dev"))
                                ), mapOf()
                            ),
                            Valid(false)
                        )
                    )
                }
            }
            should("bootstrap") {
                launch {
                    store.keys.outdatedKeys.first { it.contains(alice) }
                    store.keys.outdatedKeys.value = setOf()
                }
                val result = cut.bootstrapCrossSigning()

                assertSoftly(result) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                trust.trustAndSignKeysCalled.value shouldBe (setOf(
                    Ed25519Key("A_MSK", "A_MSK"),
                    Ed25519Key(aliceDevice, "dev")
                ) to alice)
                backup.bootstrapRoomKeyBackupCalled.value shouldBe true
                store.keys.secrets.value.keys shouldBe setOf(
                    AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
                )
                secretKeyEventContentCalled shouldBe true
                capturedPassphrase shouldBe null
                defaultSecretKeyEventContentCalled shouldBe true
                masterKeyEventContentCalled shouldBe true
                userSigningKeyEventContentCalled shouldBe true
                selfSigningKeyEventContentCalled shouldBe true
                setCrossSigningKeysCalled shouldBe true
            }
            should("bootstrap from passphrase") {
                launch {
                    store.keys.outdatedKeys.first { it.contains(alice) }
                    store.keys.outdatedKeys.value = setOf()
                }
                val result = cut.bootstrapCrossSigningFromPassphrase("super secret. not.")
                assertSoftly(result) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                trust.trustAndSignKeysCalled.value shouldBe (setOf(
                    Ed25519Key("A_MSK", "A_MSK"),
                    Ed25519Key(aliceDevice, "dev")
                ) to alice)
                backup.bootstrapRoomKeyBackupCalled.value shouldBe true
                store.keys.secrets.value.keys shouldBe setOf(
                    AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
                )
                secretKeyEventContentCalled shouldBe true
                capturedPassphrase.shouldBeInstanceOf<AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2>()
                defaultSecretKeyEventContentCalled shouldBe true
                masterKeyEventContentCalled shouldBe true
                userSigningKeyEventContentCalled shouldBe true
                selfSigningKeyEventContentCalled shouldBe true
                setCrossSigningKeysCalled shouldBe true
            }
        }
    }
}