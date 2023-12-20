package net.folivo.trixnity.client.key

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.clearOutdatedKeys
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.clientserverapi.model.keys.AddSignatures
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.key.encryptSecret
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.random.Random

class KeyTrustServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICE_DEVICE"
    val bob = UserId("bob", "server")
    val bobDevice = "BOB_DEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore

    val signServiceMock = SignServiceMock()

    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: KeyTrustServiceImpl

    beforeTest {
        signServiceMock.returnVerify = VerifyResult.Valid

        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyTrustServiceImpl(
            UserInfo(alice, aliceDevice, Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
            keyStore, globalAccountDataStore,
            signServiceMock, api
        )
    }

    afterTest {
        scope.cancel()
    }


    context(KeyTrustServiceImpl::checkOwnAdvertisedMasterKeyAndVerifySelf.name) {
        val recoveryKey = Random.nextBytes(32)
        val iv = Random.nextBytes(16)
        val keyInfo = SecretKeyEventContent.AesHmacSha2Key(
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
            globalAccountDataStore.save(GlobalAccountDataEvent(encryptedMasterSigningKey))
            val publicKey = Random.nextBytes(32).encodeUnpaddedBase64()
            keyStore.updateCrossSigningKeys(alice) {
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
            var addSignaturesRequest: Map<UserId, Map<String, JsonElement>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(AddSignatures()) {
                    addSignaturesRequest = it
                    AddSignatures.Response(mapOf())
                }
            }

            globalAccountDataStore.save(GlobalAccountDataEvent(encryptedMasterSigningKey))
            val aliceMasterKey = CrossSigningKeys(
                alice, setOf(MasterKey), keysOf(
                    Ed25519Key(masterSigningPublicKey, masterSigningPublicKey)
                )
            )
            keyStore.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            aliceMasterKey, mapOf()
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

            cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).getOrThrow()

            addSignaturesRequest shouldBe (
                    mapOf(
                        alice to mapOf(
                            masterSigningPublicKey to json.encodeToJsonElement(aliceMasterKey)
                        )
                    )
                    )
        }
    }
    context(KeyTrustServiceImpl::updateTrustLevelOfKeyChainSignedBy.name) {
        clearOutdatedKeys { keyStore }
        val aliceSigningKey1 = Ed25519Key(aliceDevice, "signingValue1")
        val aliceSigningKey2 = Ed25519Key("OTHER_ALICE", "signingValue2")
        val bobSignedKey = Ed25519Key(bobDevice, "signedValue")
        beforeTest {
            // this is a key chain with loop -> it should not loop
            keyStore.saveKeyChainLink(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = aliceSigningKey1,
                    signedUserId = alice,
                    signedKey = aliceSigningKey2
                )
            )
            keyStore.saveKeyChainLink(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = aliceSigningKey2,
                    signedUserId = bob,
                    signedKey = bobSignedKey
                )
            )
            keyStore.saveKeyChainLink(
                KeyChainLink(
                    signingUserId = bob,
                    signingKey = bobSignedKey,
                    signedUserId = alice,
                    signedKey = aliceSigningKey1
                )
            )
        }
        should("calculate trust level and update device keys") {
            val bobKey = StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        bob, bobDevice, setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                        keysOf(bobSignedKey)
                    ),
                    mapOf()
                ), Invalid("why not")
            )
            keyStore.updateDeviceKeys(bob) { mapOf(bobDevice to bobKey) }
            cut.updateTrustLevelOfKeyChainSignedBy(alice, aliceSigningKey1)
            keyStore.getDeviceKey(bob, bobDevice).first() shouldBe bobKey.copy(trustLevel = Valid(false))
        }
        should("calculate trust level and update cross signing keys") {
            val key = StoredCrossSigningKeys(
                Signed(
                    CrossSigningKeys(bob, setOf(MasterKey), keysOf(bobSignedKey)),
                    mapOf()
                ),
                Invalid("why not")
            )
            keyStore.updateCrossSigningKeys(bob) { setOf(key) }
            cut.updateTrustLevelOfKeyChainSignedBy(alice, aliceSigningKey1)
            keyStore.getCrossSigningKeys(bob).first()?.firstOrNull() shouldBe key.copy(trustLevel = CrossSigned(false))
        }
    }
    context("calculateTrustLevel") {
        clearOutdatedKeys { keyStore }
        context("without key chain") {
            val deviceKeys = Signed<DeviceKeys, UserId>(
                DeviceKeys(
                    alice, "AAAAAA", setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key("AAAAAA", "edKeyValue"),
                        Key.Curve25519Key("AAAAAA", "cuKeyValue")
                    )
                ),
                mapOf()
            )
            val masterKey = Signed<CrossSigningKeys, UserId>(
                CrossSigningKeys(
                    alice, setOf(MasterKey), keysOf(Ed25519Key("edKeyValue", "edKeyValue"))
                ), mapOf()
            )
            should("be ${NotCrossSigned::class.simpleName}, when key is verified, when master key exists") {
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))),
                                mapOf()
                            ),
                            Valid(false)
                        )
                    )
                }
                keyStore.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
            should("be ${Valid::class.simpleName} + verified, when key is verified") {
                keyStore.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(true)
            }
            should("be ${CrossSigned::class.simpleName} + verified, when key is verified and a master key") {
                keyStore.saveKeyVerificationState(
                    Ed25519Key("edKeyValue", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(true)
            }
            should("be ${Blocked::class.simpleName}, when key is blocked") {
                keyStore.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), KeyVerificationState.Blocked("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Blocked
            }
            should("be ${Valid::class.simpleName}, when there is no master key") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(false)
            }
            should("be ${CrossSigned::class.simpleName}, when it is a master key") {
                cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(false)
            }
            should("be ${NotCrossSigned::class.simpleName}, when there is a master key") {
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))),
                                mapOf()
                            ),
                            Valid(false)
                        )
                    )
                }
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
        }
        context("with master key but only self signing key chain: BOB_DEVICE <- BOB_DEVICE") {
            val deviceKeys = Signed(
                DeviceKeys(
                    bob, bobDevice, setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key(bobDevice, "..."),
                        Key.Curve25519Key(bobDevice, "...")
                    )
                ),
                mapOf(
                    bob to keysOf(Ed25519Key(bobDevice, "..."))
                )
            )
            beforeTest {
                keyStore.updateCrossSigningKeys(bob) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(MasterKey), keysOf(Ed25519Key("BOB_MSK", "..."))
                                ),
                                mapOf()
                            ), Valid(false)
                        )
                    )
                }
                keyStore.updateDeviceKeys(bob) {
                    mapOf(
                        bobDevice to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    bob,
                                    bobDevice,
                                    setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                                    keysOf(
                                        Ed25519Key(bobDevice, "..."),
                                        Key.Curve25519Key(bobDevice, "...")
                                    )
                                ),
                                mapOf()
                            ), Valid(true)
                        )
                    )
                }
                keyStore.saveKeyVerificationState(
                    Ed25519Key(bobDevice, "..."), Verified("...")
                )
            }
            should("be ${NotCrossSigned::class.simpleName}") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
        }
        context("with key chain: BOB_DEVICE <- BOB_SSK <- BOB_MSK <- ALICE_USK <- ALICE_MSK <- ALICE_DEVICE") {
            val deviceKeys = Signed(
                DeviceKeys(
                    bob, bobDevice, setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key(bobDevice, "edKeyValue"),
                        Key.Curve25519Key(bobDevice, "cuKeyValue")
                    )
                ),
                mapOf(
                    bob to keysOf(Ed25519Key("BOB_SSK", "..."))
                )
            )
            beforeTest {
                keyStore.updateCrossSigningKeys(bob) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(SelfSigningKey), keysOf(Ed25519Key("BOB_SSK", "..."))
                                ),
                                mapOf(bob to keysOf(Ed25519Key("BOB_MSK", "...")))
                            ), Valid(false)
                        ),
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(MasterKey), keysOf(Ed25519Key("BOB_MSK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key("ALICE_USK", "...")))
                            ), Valid(false)
                        )
                    )
                }
                keyStore.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    alice, setOf(UserSigningKey), keysOf(Ed25519Key("ALICE_USK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key("ALICE_MSK", "...")))
                            ), Valid(false)
                        ),
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    alice, setOf(MasterKey), keysOf(Ed25519Key("ALICE_MSK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key(aliceDevice, "...")))
                            ), Valid(true)
                        )
                    )
                }
                keyStore.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    alice,
                                    aliceDevice,
                                    setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                                    keysOf(
                                        Ed25519Key(aliceDevice, "..."),
                                        Key.Curve25519Key(aliceDevice, "...")
                                    )
                                ),
                                mapOf()
                            ), Valid(true)
                        )
                    )
                }
            }
            should("be ${CrossSigned::class.simpleName}, when there is a master key in key chain") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(false)
                keyStore.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                    KeyChainLink(
                        signingUserId = alice,
                        signingKey = Ed25519Key(keyId = "ALICE_MSK", value = "..."),
                        signedUserId = alice,
                        signedKey = Ed25519Key(keyId = "ALICE_USK", value = "...")
                    )
                )
            }
            should("be ${CrossSigned::class.simpleName} + verified, when there is a verified key in key chain") {
                keyStore.saveKeyVerificationState(
                    Ed25519Key(aliceDevice, "..."), Verified("...")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(true)
                keyStore.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                    KeyChainLink(
                        signingUserId = alice,
                        signingKey = Ed25519Key(keyId = "ALICE_MSK", value = "..."),
                        signedUserId = alice,
                        signedKey = Ed25519Key(keyId = "ALICE_USK", value = "...")
                    )
                )
            }
            should("be ${Blocked::class.simpleName}, when there is a blocked key in key chain") {
                keyStore.saveKeyVerificationState(
                    Ed25519Key(bobDevice, "..."), KeyVerificationState.Blocked("...")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Blocked
            }
        }
        context("with key chain: BOB_DEVICE <- BOB_SSK <- BOB_MSK") {
            val deviceKeys = Signed(
                DeviceKeys(
                    bob, bobDevice, setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key(bobDevice, "..."),
                        Key.Curve25519Key(bobDevice, "...")
                    )
                ),
                mapOf(
                    bob to keysOf(Ed25519Key("BOB_SSK", "..."))
                )
            )
            beforeTest {
                keyStore.updateCrossSigningKeys(bob) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(SelfSigningKey), keysOf(Ed25519Key("BOB_SSK", "..."))
                                ),
                                mapOf(bob to keysOf(Ed25519Key("BOB_MSK", "...")))
                            ), Valid(false)
                        ),
                        StoredCrossSigningKeys(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(MasterKey), keysOf(Ed25519Key("BOB_MSK", "..."))
                                ),
                                mapOf()
                            ), Valid(false)
                        )
                    )
                }
                keyStore.updateDeviceKeys(bob) {
                    mapOf(
                        bobDevice to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    bob,
                                    bobDevice,
                                    setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                                    keysOf(
                                        Ed25519Key(bobDevice, "..."),
                                        Key.Curve25519Key(bobDevice, "...")
                                    )
                                ),
                                mapOf()
                            ), Valid(true)
                        )
                    )
                }
                keyStore.saveKeyVerificationState(
                    Ed25519Key(bobDevice, "..."), Verified("...")
                )
            }
            should("be ${CrossSigned::class.simpleName}, when there is a master key in key chain") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(false)
            }
        }
    }
    context(KeyTrustServiceImpl::trustAndSignKeys.name) {
        should("handle own account keys") {
            var addSignaturesRequest: Map<UserId, Map<String, JsonElement>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(AddSignatures()) {
                    addSignaturesRequest = it
                    AddSignatures.Response(mapOf())
                }
            }

            val ownAccountsDeviceEdKey = Ed25519Key("AAAAAA", "valueA")
            val ownMasterEdKey = Ed25519Key("A-MASTER", "valueMasterA")
            val otherCrossSigningEdKey = Ed25519Key("A-SELF_SIGN", "valueSelfSignA") // should be ignored

            val ownAccountsDeviceKey = DeviceKeys(
                userId = alice,
                deviceId = "AAAAAA",
                algorithms = setOf(),
                keys = keysOf(ownAccountsDeviceEdKey)
            )
            val ownMasterKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(MasterKey),
                keys = keysOf(ownMasterEdKey)
            )
            val selfSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(SelfSigningKey),
                keys = keysOf(Ed25519Key("A-SSK", "A-SSK-value"))
            )
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(
                            SelfSigningKeyEventContent(mapOf())
                        ), ""
                    )
                )
            }
            val otherCrossSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(UserSigningKey),
                keys = keysOf(otherCrossSigningEdKey)
            )
            keyStore.updateDeviceKeys(alice) {
                mapOf("AAAAAA" to StoredDeviceKeys(Signed(ownAccountsDeviceKey, mapOf()), Valid(false)))
            }
            keyStore.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(Signed(ownMasterKey, mapOf()), Valid(false)),
                    StoredCrossSigningKeys(Signed(selfSigningKey, mapOf()), Valid(false)),
                    StoredCrossSigningKeys(Signed(otherCrossSigningKey, mapOf()), Valid(false))
                )
            }

            cut.trustAndSignKeys(
                keys = setOf(ownAccountsDeviceEdKey, ownMasterEdKey, otherCrossSigningEdKey),
                userId = alice,
            )

            addSignaturesRequest shouldBe (
                    mapOf(
                        alice to mapOf(
                            "AAAAAA" to json.encodeToJsonElement(ownAccountsDeviceKey),
                            ownMasterEdKey.value to json.encodeToJsonElement(ownMasterKey)
                        )
                    )
                    )
            keyStore.getKeyVerificationState(ownAccountsDeviceEdKey)
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
            keyStore.getKeyVerificationState(ownMasterEdKey)
                .shouldBe(Verified(ownMasterEdKey.value))
            keyStore.getKeyVerificationState(otherCrossSigningEdKey)
                .shouldBe(Verified(otherCrossSigningEdKey.value))
        }
        should("handle others account keys") {
            var addSignaturesRequest: Map<UserId, Map<String, JsonElement>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(AddSignatures()) {
                    addSignaturesRequest = it
                    AddSignatures.Response(mapOf())
                }
            }

            val othersDeviceEdKey = Ed25519Key("BBBBBB", "valueB") // should be ignored
            val othersMasterEdKey = Ed25519Key("B-MASTER", "valueMasterB")

            val othersDeviceKey = DeviceKeys(
                userId = bob,
                deviceId = "BBBBBB",
                algorithms = setOf(),
                keys = keysOf(othersDeviceEdKey)
            )
            val othersMasterKey = CrossSigningKeys(
                userId = bob,
                usage = setOf(MasterKey),
                keys = keysOf(othersMasterEdKey)
            )
            val userSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(UserSigningKey),
                keys = keysOf(Ed25519Key("A-USK", "A-USK-value"))
            )

            keyStore.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(Signed(userSigningKey, mapOf()), Valid(false)),
                )
            }
            keyStore.updateDeviceKeys(bob) {
                mapOf("BBBBBB" to StoredDeviceKeys(Signed(othersDeviceKey, mapOf()), Valid(false)))
            }
            keyStore.updateCrossSigningKeys(bob) {
                setOf(StoredCrossSigningKeys(Signed(othersMasterKey, mapOf()), Valid(false)))
            }
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(
                            UserSigningKeyEventContent(mapOf())
                        ), ""
                    )
                )
            }

            cut.trustAndSignKeys(
                keys = setOf(othersDeviceEdKey, othersMasterEdKey),
                userId = bob,
            )

            addSignaturesRequest shouldBe (
                    mapOf(
                        bob to mapOf(
                            othersMasterEdKey.value to json.encodeToJsonElement(othersMasterKey)
                        )
                    )
                    )
            keyStore.getKeyVerificationState(othersDeviceEdKey)
                .shouldBe(Verified(othersDeviceEdKey.value))
            keyStore.getKeyVerificationState(othersMasterEdKey)
                .shouldBe(Verified(othersMasterEdKey.value))
        }
        should("throw exception, when signature upload fails") {
            apiConfig.endpoints {
                matrixJsonEndpoint(AddSignatures()) {
                    AddSignatures.Response(mapOf(alice to mapOf("AAAAAA" to JsonPrimitive("oh"))))
                }
            }

            val ownAccountsDeviceEdKey = Ed25519Key("AAAAAA", "valueA")

            val ownAccountsDeviceKey = DeviceKeys(
                userId = alice,
                deviceId = "AAAAAA",
                algorithms = setOf(),
                keys = keysOf(ownAccountsDeviceEdKey)
            )
            keyStore.updateDeviceKeys(alice) {
                mapOf("AAAAAA" to StoredDeviceKeys(Signed(ownAccountsDeviceKey, mapOf()), Valid(false)))
            }
            val selfSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(SelfSigningKey),
                keys = keysOf(Ed25519Key("A-SSK", "A-SSK-value"))
            )
            keyStore.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(Signed(selfSigningKey, mapOf()), Valid(false)),
                )
            }
            keyStore.updateSecrets {
                mapOf(
                    SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                        GlobalAccountDataEvent(
                            SelfSigningKeyEventContent(mapOf())
                        ), ""
                    )
                )
            }

            shouldThrow<UploadSignaturesException> {
                cut.trustAndSignKeys(
                    keys = setOf(ownAccountsDeviceEdKey),
                    userId = alice,
                )
            }

            keyStore.getKeyVerificationState(ownAccountsDeviceEdKey)
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
        }
    }
}