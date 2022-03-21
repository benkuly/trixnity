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
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmSignService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.keys.SetCrossSigningKeys
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.random.Random

class KeyServiceCrossSigningTest : ShouldSpec(body)

@OptIn(InternalAPI::class)
private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val json = createMatrixJson()
    val mappings = createEventContentSerializerMappings()
    val backup: KeyBackupService = mockk()
    val trust: KeyTrustService = mockk()
    lateinit var apiConfig: PortableMockEngineConfig
    val currentSyncState = MutableStateFlow(SyncApiClient.SyncState.STOPPED)

    mockkStatic(::decryptSecret)

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyService(
            "",
            alice,
            aliceDevice,
            store,
            olm,
            api,
            backup = backup,
            trust = trust,
            currentSyncState = currentSyncState
        )
        coEvery { olm.sign.verify(any<SignedDeviceKeys>(), any()) } returns VerifyResult.Valid
        coEvery { olm.sign.verify(any<SignedCrossSigningKeys>(), any()) } returns VerifyResult.Valid
    }

    afterTest {
        clearAllMocks()
        scope.cancel()
    }

    context(KeyService::checkOwnAdvertisedMasterKeyAndVerifySelf.name) {
        beforeTest {
            coEvery { trust.trustAndSignKeys(any(), any()) } just Runs
        }
        should("fail when master key cannot be found") {
            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
        }
        should("fail when master key does not match") {
            val encryptedMasterKey = MasterKeyEventContent(mapOf())
            store.globalAccountData.update(Event.GlobalAccountDataEvent(encryptedMasterKey))
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

            coEvery { decryptSecret(any(), any(), any(), any(), any(), any()) } returns Random.nextBytes(32)
                .encodeBase64()

            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
        }
        should("be success, when master key matches") {
            val encryptedMasterKey = MasterKeyEventContent(mapOf())
            store.globalAccountData.update(Event.GlobalAccountDataEvent(encryptedMasterKey))
            val privateKey = Random.nextBytes(32).encodeBase64()
            val publicKey = freeAfter(OlmPkSigning.create(privateKey)) { it.publicKey }
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                    Ed25519Key(publicKey, publicKey)
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

            coEvery { decryptSecret(any(), any(), any(), any(), any(), any()) } returns privateKey

            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).getOrThrow()

            coVerify {
                trust.trustAndSignKeys(
                    setOf(
                        Ed25519Key(publicKey, publicKey),
                        Ed25519Key(aliceDevice, "dev")
                    ), alice
                )
            }
        }
    }
    context(KeyService::bootstrapCrossSigning.name) {
        context("successfull") {
            var secretKeyEventContentCalled = false
            var capturedPassphrase: SecretKeyEventContent.SecretStorageKeyPassphrase? = null
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
                        it.shouldBeInstanceOf<SecretKeyEventContent.AesHmacSha2Key>()
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
                coEvery { olm.sign.sign(any<CrossSigningKeys>(), any<OlmSignService.SignWith>()) }.answers {
                    Signed(firstArg(), mapOf())
                }
                coEvery {
                    backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                } returns Result.success(Unit)
                coEvery { trust.trustAndSignKeys(any(), any()) } just Runs
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
                val result = async { cut.bootstrapCrossSigning() }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                coVerify {
                    trust.trustAndSignKeys(
                        setOf(
                            Ed25519Key("A_MSK", "A_MSK"),
                            Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                    backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                }
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
                val result = async { cut.bootstrapCrossSigningFromPassphrase("super secret. not.") }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.Success(Unit))
                }
                coVerify {
                    trust.trustAndSignKeys(
                        setOf(
                            Ed25519Key("A_MSK", "A_MSK"),
                            Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                    backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                }
                store.keys.secrets.value.keys shouldBe setOf(
                    AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
                )
                secretKeyEventContentCalled shouldBe true
                capturedPassphrase.shouldBeInstanceOf<SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2>()
                defaultSecretKeyEventContentCalled shouldBe true
                masterKeyEventContentCalled shouldBe true
                userSigningKeyEventContentCalled shouldBe true
                selfSigningKeyEventContentCalled shouldBe true
                setCrossSigningKeysCalled shouldBe true
            }
        }
    }
}