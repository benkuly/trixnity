package net.folivo.trixnity.client.key

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.crypto.getDeviceKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.client.verification.KeyVerificationState.Verified
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.AddSignatures
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key

class KeyTrustServiceTest : ShouldSpec(body)

@OptIn(InternalAPI::class)
private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICE_DEVICE"
    val bob = UserId("bob", "server")
    val bobDevice = "BOB_DEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val api = mockk<MatrixClientServerApiClient>()

    lateinit var cut: KeyTrustService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        cut = KeyTrustService(alice, store, olm, api)
        coEvery { olm.sign.verify(any<SignedDeviceKeys>(), any()) } returns VerifyResult.Valid
        coEvery { olm.sign.verify(any<SignedCrossSigningKeys>(), any()) } returns VerifyResult.Valid
    }

    afterTest {
        clearAllMocks()
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
                    Ed25519Key("AAAAAA", "edKeyValue"), alice, "AAAAAA",
                    Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
            should("be ${Valid::class.simpleName} + verified, when key is verified") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), alice, "AAAAAA",
                    Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(true)
            }
            should("be ${CrossSigned::class.simpleName} + verified, when key is verified and a master key") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("edKeyValue", "edKeyValue"), alice, null,
                    Verified("edKeyValue")
                )
                cut.calculateCrossSigningKeysTrustLevel(masterKey) shouldBe CrossSigned(true)
            }
            should("be ${Blocked::class.simpleName}, when key is blocked") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), alice, "AAAAAA",
                    KeyVerificationState.Blocked("edKeyValue")
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
                    Ed25519Key(bobDevice, "..."), bob, bobDevice,
                    Verified("...")
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
                    Ed25519Key(aliceDevice, "..."), alice, aliceDevice,
                    Verified("...")
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
                    Ed25519Key(bobDevice, "..."), bob, bobDevice,
                    KeyVerificationState.Blocked("...")
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
                    Ed25519Key(bobDevice, "..."), bob, bobDevice,
                    Verified("...")
                )
            }
            should("be ${CrossSigned::class.simpleName}, when there is a master key in key chain") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(false)
            }
        }
    }
    context(KeyTrustService::trustAndSignKeys.name) {
        beforeTest {
            coEvery { olm.sign.sign<DeviceKeys>(any(), any<SignWith>()) }.coAnswers {
                Signed(firstArg(), mapOf())
            }
            coEvery { olm.sign.sign<CrossSigningKeys>(any(), any<SignWith>()) }.coAnswers {
                Signed(firstArg(), mapOf())
            }
        }
        should("handle own account keys") {
            coEvery {
                api.keys.addSignatures(any(), any(), any())
            } returns Result.success(AddSignatures.Response(mapOf()))

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
                    StoredCrossSigningKeys(Signed(otherCrossSigningKey, mapOf()), Valid(false))
                )
            }

            cut.trustAndSignKeys(
                keys = setOf(ownAccountsDeviceEdKey, ownMasterEdKey, otherCrossSigningEdKey),
                userId = alice,
            )

            coVerify {
                api.keys.addSignatures(
                    setOf(Signed(ownAccountsDeviceKey, mapOf())),
                    setOf(Signed(ownMasterKey, mapOf()))
                )
            }
            store.keys.getKeyVerificationState(ownAccountsDeviceEdKey, alice, "AAAAAA")
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
            store.keys.getKeyVerificationState(ownMasterEdKey, alice, null)
                .shouldBe(Verified(ownMasterEdKey.value))
            store.keys.getKeyVerificationState(otherCrossSigningEdKey, alice, null)
                .shouldBe(Verified(otherCrossSigningEdKey.value))
        }
        should("handle others account keys") {
            coEvery {
                api.keys.addSignatures(any(), any(), any())
            } returns Result.success(AddSignatures.Response(mapOf()))

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

            store.keys.updateDeviceKeys(bob) {
                mapOf("BBBBBB" to StoredDeviceKeys(Signed(othersDeviceKey, mapOf()), Valid(false)))
            }
            store.keys.updateCrossSigningKeys(bob) {
                setOf(StoredCrossSigningKeys(Signed(othersMasterKey, mapOf()), Valid(false)))
            }

            cut.trustAndSignKeys(
                keys = setOf(othersDeviceEdKey, othersMasterEdKey),
                userId = bob,
            )

            coVerify {
                api.keys.addSignatures(
                    setOf(),
                    setOf(Signed(othersMasterKey, mapOf()))
                )
            }
            store.keys.getKeyVerificationState(othersDeviceEdKey, bob, "BBBBBB")
                .shouldBe(Verified(othersDeviceEdKey.value))
            store.keys.getKeyVerificationState(othersMasterEdKey, bob, null)
                .shouldBe(Verified(othersMasterEdKey.value))
        }
        should("throw exception, when signature upload fails") {
            coEvery {
                api.keys.addSignatures(any(), any(), any())
            } returns Result.success(AddSignatures.Response(mapOf(alice to mapOf("AAAAAA" to JsonPrimitive("oh")))))

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

            shouldThrow<UploadSignaturesException> {
                cut.trustAndSignKeys(
                    keys = setOf(ownAccountsDeviceEdKey),
                    userId = alice,
                )
            }

            store.keys.getKeyVerificationState(ownAccountsDeviceEdKey, alice, "AAAAAA")
                .shouldBe(Verified(ownAccountsDeviceEdKey.value))
        }
    }
}