package net.folivo.trixnity.client.key

import com.soywiz.krypto.SecureRandom
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.keys.SetCrossSigningKeys
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.crypto.SecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.crypto.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.key.recoveryKeyFromPassphrase
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class KeyServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 120_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var olmCryptoStore: OlmCryptoStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore
    lateinit var signServiceMock: SignServiceMock
    lateinit var keyBackupServiceMock: KeyBackupServiceMock
    lateinit var keyTrustServiceMock: KeyTrustServiceMock
    val json = createMatrixEventJson()
    val mappings = createEventContentSerializerMappings()
    lateinit var olmSign: SignServiceMock
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: KeyServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        olmSign = SignServiceMock()
        keyStore = getInMemoryKeyStore(scope)
        olmCryptoStore = getInMemoryOlmStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        signServiceMock = SignServiceMock()
        keyBackupServiceMock = KeyBackupServiceMock()
        keyTrustServiceMock = KeyTrustServiceMock()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyServiceImpl(
            UserInfo(alice, aliceDevice, Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            keyStore,
            olmCryptoStore,
            globalAccountDataStore,
            signServiceMock,
            keyBackupServiceMock,
            keyTrustServiceMock,
            api,
        )
        olmSign.returnVerify = VerifyResult.Valid
    }

    afterTest {
        scope.cancel()
    }

    context(KeyServiceImpl::bootstrapCrossSigning.name) {
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
                keyStore.updateCrossSigningKeys(alice) {
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
                keyStore.updateDeviceKeys(alice) {
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
                    keyStore.outdatedKeys.first { it.contains(alice) }
                    keyStore.updateOutdatedKeys { setOf() }
                    keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
                    keyStore.updateDeviceKeys(alice) {
                        mapOf(
                            aliceDevice to StoredDeviceKeys(
                                SignedDeviceKeys(
                                    DeviceKeys(
                                        alice, aliceDevice, setOf(),
                                        keysOf(Ed25519Key(aliceDevice, "dev"))
                                    ), mapOf()
                                ),
                                KeySignatureTrustLevel.CrossSigned(true)
                            )
                        )
                    }
                }
                val result = cut.bootstrapCrossSigning()

                assertSoftly(result) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
                    Ed25519Key("A_MSK", "A_MSK"),
                    Ed25519Key(aliceDevice, "dev")
                ) to alice)
                keyBackupServiceMock.bootstrapRoomKeyBackupCalled.value shouldBe true
                keyStore.secrets.value.keys shouldBe setOf(
                    M_CROSS_SIGNING_SELF_SIGNING,
                    M_CROSS_SIGNING_USER_SIGNING
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
                    keyStore.outdatedKeys.first { it.contains(alice) }
                    keyStore.updateOutdatedKeys { setOf() }
                    keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
                    keyStore.updateDeviceKeys(alice) {
                        mapOf(
                            aliceDevice to StoredDeviceKeys(
                                SignedDeviceKeys(
                                    DeviceKeys(
                                        alice, aliceDevice, setOf(),
                                        keysOf(Ed25519Key(aliceDevice, "dev"))
                                    ), mapOf()
                                ),
                                KeySignatureTrustLevel.CrossSigned(true)
                            )
                        )
                    }
                }
                val result = cut.bootstrapCrossSigningFromPassphrase("super secret. not.") {
                    val passphraseInfo = AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2(
                        salt = SecureRandom.nextBytes(32).encodeBase64(),
                        iterations = 1_000,  // just a test, not secure
                        bits = 32 * 8
                    )
                    val iv = SecureRandom.nextBytes(16)
                    val key = recoveryKeyFromPassphrase("super secret. not.", passphraseInfo)
                    key to AesHmacSha2Key(
                        passphrase = passphraseInfo,
                        iv = iv.encodeBase64(),
                        mac = createAesHmacSha2MacFromKey(key = key, iv = iv)
                    )
                }
                assertSoftly(result) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
                    Ed25519Key("A_MSK", "A_MSK"),
                    Ed25519Key(aliceDevice, "dev")
                ) to alice)
                keyBackupServiceMock.bootstrapRoomKeyBackupCalled.value shouldBe true
                keyStore.secrets.value.keys shouldBe setOf(
                    M_CROSS_SIGNING_SELF_SIGNING,
                    M_CROSS_SIGNING_USER_SIGNING
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