package net.folivo.trixnity.client.key

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
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
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.crypto.key.encryptSecret
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.getValue
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.suspendLazy
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.random.Random
import kotlin.test.Test

class KeyTrustServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "ALICE_DEVICE"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BOB_DEVICE"

    private val keyStore = getInMemoryKeyStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val signServiceMock = SignServiceMock().apply {
        returnVerify = VerifyResult.Valid
    }

    private val json = createMatrixEventJson()
    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig, json)

    private val cut = KeyTrustServiceImpl(
        UserInfo(alice, aliceDevice, Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        keyStore,
        globalAccountDataStore,
        signServiceMock,
        api,
        driver
    )

    private val recoveryKey = Random.nextBytes(32)
    private val iv = Random.nextBytes(16)
    private val keyInfo by suspendLazy {
        SecretKeyEventContent.AesHmacSha2Key(
            iv = iv.encodeBase64(),
            mac = createAesHmacSha2MacFromKey(recoveryKey, iv)
        )
    }
    private val keyId = "keyId"

    private val _masterSigningKeys = driver.key.ed25519SecretKey()
    private val masterSigningPrivateKey = _masterSigningKeys.base64
    private val masterSigningPublicKey = _masterSigningKeys.publicKey.base64

    private val encryptedMasterSigningKey by suspendLazy {
        MasterKeyEventContent(
            encryptSecret(recoveryKey, keyId, "m.cross_signing.master", masterSigningPrivateKey, json)
        )
    }

    private val aliceSigningKey1 = Ed25519Key(aliceDevice, "signingValue1")
    private val aliceSigningKey2 = Ed25519Key("OTHER_ALICE", "signingValue2")
    private val bobSignedKey = Ed25519Key(bobDevice, "signedValue")

    @Test
    fun `checkOwnAdvertisedMasterKeyAndVerifySelf » fail when master key cannot be found`() = runTest {
        cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).isFailure shouldBe true
    }

    @Test
    fun `checkOwnAdvertisedMasterKeyAndVerifySelf » fail when master key does not match`() = runTest {
        globalAccountDataStore.save(GlobalAccountDataEvent(encryptedMasterSigningKey))
        val publicKey = Random.nextBytes(32).encodeUnpaddedBase64()
        keyStore.updateCrossSigningKeys(alice) {
            setOf(
                StoredCrossSigningKeys(
                    SignedCrossSigningKeys(
                        CrossSigningKeys(
                            alice, setOf(UserSigningKey), keysOf(
                                Ed25519Key(publicKey, publicKey)
                            )
                        ), mapOf()
                    ), CrossSigned(true)
                )
            )
        }

        cut.checkOwnAdvertisedMasterKeyAndVerifySelf(recoveryKey, keyId, keyInfo).isFailure shouldBe true
    }

    @Test
    fun `checkOwnAdvertisedMasterKeyAndVerifySelf » be success when master key matches`() = runTest {
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

    private suspend fun TestScope.updateTrustLevelOfKeyChainSignedBySetup() {
        clearOutdatedKeys { keyStore }
        // this is a keychain with loop -> it should not loop
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


    @Test
    fun `updateTrustLevelOfKeyChainSignedBy » calculate trust level and update device keys`() = runTest {
        updateTrustLevelOfKeyChainSignedBySetup()
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

    @Test
    fun `updateTrustLevelOfKeyChainSignedBy » calculate trust level and update cross signing keys`() =
        runTest {
            updateTrustLevelOfKeyChainSignedBySetup()
            val key = StoredCrossSigningKeys(
                Signed(
                    CrossSigningKeys(bob, setOf(MasterKey), keysOf(bobSignedKey)),
                    mapOf()
                ),
                Invalid("why not")
            )
            keyStore.updateCrossSigningKeys(bob) { setOf(key) }
            cut.updateTrustLevelOfKeyChainSignedBy(alice, aliceSigningKey1)
            keyStore.getCrossSigningKeys(bob).first()?.firstOrNull() shouldBe key.copy(
                trustLevel = CrossSigned(
                    false
                )
            )
        }

    private val withoutKeyChaindeviceKeys = Signed<DeviceKeys, UserId>(
        DeviceKeys(
            alice, "AAAAAA", setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
            keysOf(
                Ed25519Key("AAAAAA", "edKeyValue"),
                Key.Curve25519Key("AAAAAA", "cuKeyValue")
            )
        ),
        mapOf()
    )
    private val masterKey = Signed<CrossSigningKeys, UserId>(
        CrossSigningKeys(
            alice, setOf(MasterKey), keysOf(Ed25519Key("edKeyValue", "edKeyValue"))
        ), mapOf()
    )

    private fun TestScope.calculateTrustLevelSetup() {
        clearOutdatedKeys { keyStore }
    }

    @Test
    fun `calculateTrustLevel » without key chain » be NotCrossSigned when key is verified when master key exists`() =
        runTest {
            calculateTrustLevelSetup()
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
            cut.calculateDeviceKeysTrustLevel(withoutKeyChaindeviceKeys) shouldBe NotCrossSigned
        }

    @Test
    fun `calculateTrustLevel » without key chain » be Valid and verified when key is verified`() = runTest {
        calculateTrustLevelSetup()
        keyStore.saveKeyVerificationState(
            Ed25519Key("AAAAAA", "edKeyValue"), Verified("edKeyValue")
        )
        cut.calculateDeviceKeysTrustLevel(withoutKeyChaindeviceKeys) shouldBe Valid(true)
    }

    @Test
    fun `calculateTrustLevel » without key chain » be CrossSigned and verified when key is verified and a master key`() =
        runTest {
            calculateTrustLevelSetup()
            keyStore.saveKeyVerificationState(
                Ed25519Key("edKeyValue", "edKeyValue"), Verified("edKeyValue")
            )
            cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(true)
        }

    @Test
    fun `calculateTrustLevel » without key chain » be Blocked when key is blocked`() = runTest {
        calculateTrustLevelSetup()
        keyStore.saveKeyVerificationState(
            Ed25519Key("AAAAAA", "edKeyValue"), KeyVerificationState.Blocked("edKeyValue")
        )
        cut.calculateDeviceKeysTrustLevel(withoutKeyChaindeviceKeys) shouldBe Blocked
    }

    @Test
    fun `calculateTrustLevel » without key chain » be Valid when there is no master key`() = runTest {
        calculateTrustLevelSetup()
        cut.calculateDeviceKeysTrustLevel(withoutKeyChaindeviceKeys) shouldBe Valid(false)
    }

    @Test
    fun `calculateTrustLevel » without key chain » be CrossSigned when it is a master key`() = runTest {
        calculateTrustLevelSetup()
        cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(false)
    }

    @Test
    fun `calculateTrustLevel » without key chain » be NotCrossSigned when there is a master key`() = runTest {
        calculateTrustLevelSetup()
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
        cut.calculateDeviceKeysTrustLevel(withoutKeyChaindeviceKeys) shouldBe NotCrossSigned
    }

    private val selfSigningChaindeviceKeys = Signed(
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

    private suspend fun TestScope.selfSigningChainSetup() {
        calculateTrustLevelSetup()
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

    @Test
    fun `calculateTrustLevel » with master key but only self signing key chain BOB_DEVICE - BOB_DEVICE » be NotCrossSigned`() =
        runTest {
            selfSigningChainSetup()
            cut.calculateDeviceKeysTrustLevel(selfSigningChaindeviceKeys) shouldBe NotCrossSigned
        }

    private val keyChaindeviceKeys = Signed(
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

    private suspend fun TestScope.withKeyChainSetup() {
        calculateTrustLevelSetup()
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

    @Test
    fun `calculateTrustLevel » with key chain BOB_DEVICE - BOB_SSK - BOB_MSK - ALICE_USK - ALICE_MSK - ALICE_DEVICE » be CrossSigned when there is a master key in key chain`() =
        runTest {
            withKeyChainSetup()
            cut.calculateDeviceKeysTrustLevel(keyChaindeviceKeys) shouldBe CrossSigned(false)
            keyStore.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = Ed25519Key(id = "ALICE_MSK", value = "..."),
                    signedUserId = alice,
                    signedKey = Ed25519Key(id = "ALICE_USK", value = "...")
                )
            )
        }

    @Test
    fun `calculateTrustLevel » with key chain BOB_DEVICE - BOB_SSK - BOB_MSK - ALICE_USK - ALICE_MSK - ALICE_DEVICE » be CrossSigned and verified when there is a verified key in key chain`() =
        runTest {
            withKeyChainSetup()
            keyStore.saveKeyVerificationState(
                Ed25519Key(aliceDevice, "..."), Verified("...")
            )
            cut.calculateDeviceKeysTrustLevel(keyChaindeviceKeys) shouldBe CrossSigned(true)
            keyStore.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = Ed25519Key(id = "ALICE_MSK", value = "..."),
                    signedUserId = alice,
                    signedKey = Ed25519Key(id = "ALICE_USK", value = "...")
                )
            )
        }

    @Test
    fun `calculateTrustLevel » with key chain BOB_DEVICE - BOB_SSK - BOB_MSK - ALICE_USK - ALICE_MSK - ALICE_DEVICE » be Blocked when there is a blocked key in key chain`() =
        runTest {
            withKeyChainSetup()
            keyStore.saveKeyVerificationState(
                Ed25519Key(bobDevice, "..."), KeyVerificationState.Blocked("...")
            )
            cut.calculateDeviceKeysTrustLevel(keyChaindeviceKeys) shouldBe Blocked
        }


    private val simpleKeyChaindeviceKeys = Signed(
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

    private suspend fun TestScope.simpleKeyChainSetup() {
        calculateTrustLevelSetup()
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

    @Test
    fun `calculateTrustLevel » with key chain BOB_DEVICE - BOB_SSK - BOB_MSK » be CrossSigned when there is a master key in key chain`() =
        runTest {
            simpleKeyChainSetup()
            cut.calculateDeviceKeysTrustLevel(simpleKeyChaindeviceKeys) shouldBe CrossSigned(false)
        }

    @Test
    fun `trustAndSignKeys » handle own account keys`() = runTest {
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
                        ownMasterEdKey.value.value to json.encodeToJsonElement(ownMasterKey)
                    )
                )
                )
        keyStore.getKeyVerificationState(ownAccountsDeviceEdKey)
            .shouldBe(Verified(ownAccountsDeviceEdKey.value.value))
        keyStore.getKeyVerificationState(ownMasterEdKey)
            .shouldBe(Verified(ownMasterEdKey.value.value))
        keyStore.getKeyVerificationState(otherCrossSigningEdKey)
            .shouldBe(Verified(otherCrossSigningEdKey.value.value))
    }

    @Test
    fun `trustAndSignKeys » handle others account keys`() = runTest {
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
                        othersMasterEdKey.value.value to json.encodeToJsonElement(othersMasterKey)
                    )
                )
                )
        keyStore.getKeyVerificationState(othersDeviceEdKey)
            .shouldBe(Verified(othersDeviceEdKey.value.value))
        keyStore.getKeyVerificationState(othersMasterEdKey)
            .shouldBe(Verified(othersMasterEdKey.value.value))
    }

    @Test
    fun `trustAndSignKeys » throw exception when signature upload fails`() = runTest {
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
            .shouldBe(Verified(ownAccountsDeviceEdKey.value.value))
    }
}