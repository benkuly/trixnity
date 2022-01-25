package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmSignService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
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
    val api = mockk<MatrixApiClient>()
    val json = createMatrixJson()

    mockkStatic(::decryptSecret)

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        coEvery { api.json } returns json
        cut = KeyService("", alice, aliceDevice, store, olm, api)
        coEvery { olm.sign.verify(any<SignedDeviceKeys>(), any()) } returns VerifyResult.Valid
        coEvery { olm.sign.verify(any<SignedCrossSigningKeys>(), any()) } returns VerifyResult.Valid
    }

    afterTest {
        clearAllMocks()
        scope.cancel()
    }

    context(KeyService::checkOwnAdvertisedMasterKeyAndVerifySelf.name) {
        lateinit var spyCut: KeyService
        beforeTest {
            spyCut = spyk(cut)
            coEvery { spyCut.trustAndSignKeys(any(), any()) } just Runs
        }
        should("fail when master key cannot be found") {
            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
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

            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
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

            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).getOrThrow()

            coVerify {
                spyCut.trustAndSignKeys(
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
            lateinit var spyCut: KeyService
            beforeTest {
                spyCut = spyk(cut)

                coEvery { api.json } returns createMatrixJson()
                coEvery { api.users.setAccountData<SecretKeyEventContent>(any(), any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<DefaultSecretKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<MasterKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<SelfSigningKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<UserSigningKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { olm.sign.sign(any<CrossSigningKeys>(), any<OlmSignService.SignWith>()) }.answers {
                    Signed(firstArg(), mapOf())
                }
                coEvery {
                    spyCut.backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                } returns Result.success(Unit)
                coEvery { api.keys.setCrossSigningKeys(any(), any(), any()) }
                    .returns(Result.success(UIA.UIASuccess(Unit)))
                coEvery { spyCut.trustAndSignKeys(any(), any()) } just Runs
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
                val result = async { spyCut.bootstrapCrossSigning() }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.UIASuccess(Unit))
                }
                coVerify {
                    api.users.setAccountData<SecretKeyEventContent>(
                        content = coWithArg {
                            it.shouldBeInstanceOf<SecretKeyEventContent.AesHmacSha2Key>()
                            it.iv shouldNot beEmpty()
                            it.mac shouldNot beEmpty()
                            it.passphrase shouldBe null
                        },
                        userId = alice,
                        key = coWithArg { it.length shouldBeGreaterThan 10 }
                    )
                    api.users.setAccountData<DefaultSecretKeyEventContent>(
                        content = coWithArg { it.key.length shouldBeGreaterThan 10 },
                        userId = alice
                    )
                    api.users.setAccountData<MasterKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<SelfSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<UserSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.keys.setCrossSigningKeys(any(), any(), any())
                    spyCut.trustAndSignKeys(
                        setOf(
                            Ed25519Key("A_MSK", "A_MSK"),
                            Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                    spyCut.backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                }
                store.keys.secrets.value.keys shouldBe setOf(
                    AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
                )
            }
            should("bootstrap from passphrase") {
                val result = async { spyCut.bootstrapCrossSigningFromPassphrase("super secret. not.") }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.UIASuccess(Unit))
                }
                coVerify {
                    api.users.setAccountData<SecretKeyEventContent>(
                        content = coWithArg {
                            it.shouldBeInstanceOf<SecretKeyEventContent.AesHmacSha2Key>()
                            it.iv shouldNot beEmpty()
                            it.mac shouldNot beEmpty()
                            assertSoftly(it.passphrase) {
                                this.shouldBeInstanceOf<SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2>()
                                this.bits shouldBe 32 * 8
                                this.iterations shouldBeGreaterThanOrEqual 500_000
                                this.salt shouldNot beEmpty()
                            }
                        },
                        userId = alice,
                        key = coWithArg { it.length shouldBeGreaterThan 10 }
                    )
                    api.users.setAccountData<DefaultSecretKeyEventContent>(
                        content = coWithArg { it.key.length shouldBeGreaterThan 10 },
                        userId = alice
                    )
                    api.users.setAccountData<MasterKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<SelfSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<UserSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.keys.setCrossSigningKeys(any(), any(), any())
                    spyCut.trustAndSignKeys(
                        setOf(
                            Ed25519Key("A_MSK", "A_MSK"),
                            Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                    spyCut.backup.bootstrapRoomKeyBackup(any(), any(), any(), any())
                }
                store.keys.secrets.value.keys shouldBe setOf(
                    AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING,
                    AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
                )
            }
        }
    }
}