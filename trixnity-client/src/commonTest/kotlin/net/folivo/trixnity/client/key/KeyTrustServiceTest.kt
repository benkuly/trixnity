package net.folivo.trixnity.client.key

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.key.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.KeyVerificationState.Verified
import net.folivo.trixnity.clientserverapi.model.keys.AddSignatures
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.sign.VerifyResult
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint

class KeyTrustServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICE_DEVICE"
    val bob = UserId("bob", "server")
    val bobDevice = "BOB_DEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store

    val olmSign = SignServiceMock()

    val json = createMatrixEventJson()
    val contentMappings = createEventContentSerializerMappings()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: KeyTrustService

    beforeTest {
        olmSign.returnVerify = VerifyResult.Valid

        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = KeyTrustService(alice, store, olmSign, api)
    }

    afterTest {
        scope.cancel()
    }


    context(KeyTrustService::updateTrustLevelOfKeyChainSignedBy.name) {
        val aliceSigningKey1 = Ed25519Key(aliceDevice, "signingValue1")
        val aliceSigningKey2 = Ed25519Key("OTHER_ALICE", "signingValue2")
        val bobSignedKey = Ed25519Key(bobDevice, "signedValue")
        beforeTest {
            // this is a key chain with loop -> it should not loop
            store.keys.saveKeyChainLink(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = aliceSigningKey1,
                    signedUserId = alice,
                    signedKey = aliceSigningKey2
                )
            )
            store.keys.saveKeyChainLink(
                KeyChainLink(
                    signingUserId = alice,
                    signingKey = aliceSigningKey2,
                    signedUserId = bob,
                    signedKey = bobSignedKey
                )
            )
            store.keys.saveKeyChainLink(
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
            store.keys.updateDeviceKeys(bob) { mapOf(bobDevice to bobKey) }
            cut.updateTrustLevelOfKeyChainSignedBy(alice, aliceSigningKey1)
            store.keys.getDeviceKey(bob, bobDevice) shouldBe bobKey.copy(trustLevel = Valid(false))
        }
        should("calculate trust level and update cross signing keys") {
            val key = StoredCrossSigningKeys(
                Signed(
                    CrossSigningKeys(bob, setOf(MasterKey), keysOf(bobSignedKey)),
                    mapOf()
                ),
                Invalid("why not")
            )
            store.keys.updateCrossSigningKeys(bob) { setOf(key) }
            cut.updateTrustLevelOfKeyChainSignedBy(alice, aliceSigningKey1)
            store.keys.getCrossSigningKeys(bob)?.firstOrNull() shouldBe key.copy(trustLevel = CrossSigned(false))
        }
    }
    context("calculateTrustLevel") {
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
                store.keys.updateCrossSigningKeys(alice) {
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
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
            should("be ${Valid::class.simpleName} + verified, when key is verified") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(true)
            }
            should("be ${CrossSigned::class.simpleName} + verified, when key is verified and a master key") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("edKeyValue", "edKeyValue"), Verified("edKeyValue")
                )
                cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(true)
            }
            should("be ${Blocked::class.simpleName}, when key is blocked") {
                store.keys.saveKeyVerificationState(
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
                store.keys.updateCrossSigningKeys(alice) {
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
                store.keys.updateCrossSigningKeys(bob) {
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
                store.keys.updateDeviceKeys(bob) {
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
                store.keys.saveKeyVerificationState(
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
                store.keys.updateCrossSigningKeys(bob) {
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
                store.keys.updateCrossSigningKeys(alice) {
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
                store.keys.updateDeviceKeys(alice) {
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
                store.keys.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                    KeyChainLink(
                        signingUserId = alice,
                        signingKey = Ed25519Key(keyId = "ALICE_MSK", value = "..."),
                        signedUserId = alice,
                        signedKey = Ed25519Key(keyId = "ALICE_USK", value = "...")
                    )
                )
            }
            should("be ${CrossSigned::class.simpleName} + verified, when there is a verified key in key chain") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key(aliceDevice, "..."), Verified("...")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(true)
                store.keys.getKeyChainLinksBySigningKey(alice, Ed25519Key("ALICE_MSK", "...")) shouldBe setOf(
                    KeyChainLink(
                        signingUserId = alice,
                        signingKey = Ed25519Key(keyId = "ALICE_MSK", value = "..."),
                        signedUserId = alice,
                        signedKey = Ed25519Key(keyId = "ALICE_USK", value = "...")
                    )
                )
            }
            should("be ${Blocked::class.simpleName}, when there is a blocked key in key chain") {
                store.keys.saveKeyVerificationState(
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
                store.keys.updateCrossSigningKeys(bob) {
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
                store.keys.updateDeviceKeys(bob) {
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
                store.keys.saveKeyVerificationState(
                    Ed25519Key(bobDevice, "..."), Verified("...")
                )
            }
            should("be ${CrossSigned::class.simpleName}, when there is a master key in key chain") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(false)
            }
        }
    }
    context(KeyTrustService::trustAndSignKeys.name) {
        should("handle own account keys") {
            var addSignaturesRequest: Map<UserId, Map<String, JsonElement>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(json, contentMappings, AddSignatures()) {
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
            store.keys.secrets.value = mapOf(
                SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(
                        SelfSigningKeyEventContent(mapOf())
                    ), ""
                )
            )
            val otherCrossSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(UserSigningKey),
                keys = keysOf(otherCrossSigningEdKey)
            )
            store.keys.updateDeviceKeys(alice) {
                mapOf("AAAAAA" to StoredDeviceKeys(Signed(ownAccountsDeviceKey, mapOf()), Valid(false)))
            }
            store.keys.updateCrossSigningKeys(alice) {
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
            store.keys.getKeyVerificationState(ownAccountsDeviceEdKey)
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
            store.keys.getKeyVerificationState(ownMasterEdKey)
                .shouldBe(Verified(ownMasterEdKey.value))
            store.keys.getKeyVerificationState(otherCrossSigningEdKey)
                .shouldBe(Verified(otherCrossSigningEdKey.value))
        }
        should("handle others account keys") {
            var addSignaturesRequest: Map<UserId, Map<String, JsonElement>>? = null
            apiConfig.endpoints {
                matrixJsonEndpoint(json, contentMappings, AddSignatures()) {
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

            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(Signed(userSigningKey, mapOf()), Valid(false)),
                )
            }
            store.keys.updateDeviceKeys(bob) {
                mapOf("BBBBBB" to StoredDeviceKeys(Signed(othersDeviceKey, mapOf()), Valid(false)))
            }
            store.keys.updateCrossSigningKeys(bob) {
                setOf(StoredCrossSigningKeys(Signed(othersMasterKey, mapOf()), Valid(false)))
            }
            store.keys.secrets.value = mapOf(
                SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(
                        UserSigningKeyEventContent(mapOf())
                    ), ""
                )
            )

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
            store.keys.getKeyVerificationState(othersDeviceEdKey)
                .shouldBe(Verified(othersDeviceEdKey.value))
            store.keys.getKeyVerificationState(othersMasterEdKey)
                .shouldBe(Verified(othersMasterEdKey.value))
        }
        should("throw exception, when signature upload fails") {
            apiConfig.endpoints {
                matrixJsonEndpoint(json, contentMappings, AddSignatures()) {
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
            store.keys.updateDeviceKeys(alice) {
                mapOf("AAAAAA" to StoredDeviceKeys(Signed(ownAccountsDeviceKey, mapOf()), Valid(false)))
            }
            val selfSigningKey = CrossSigningKeys(
                userId = alice,
                usage = setOf(SelfSigningKey),
                keys = keysOf(Ed25519Key("A-SSK", "A-SSK-value"))
            )
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(Signed(selfSigningKey, mapOf()), Valid(false)),
                )
            }
            store.keys.secrets.value = mapOf(
                SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(
                        SelfSigningKeyEventContent(mapOf())
                    ), ""
                )
            )

            shouldThrow<UploadSignaturesException> {
                cut.trustAndSignKeys(
                    keys = setOf(ownAccountsDeviceEdKey),
                    userId = alice,
                )
            }

            store.keys.getKeyVerificationState(ownAccountsDeviceEdKey)
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
        }
    }
}