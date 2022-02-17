package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysVersionResponse
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.olm.OlmPkDecryption
import net.folivo.trixnity.olm.freeAfter

class KeyBackupUtilsTest : ShouldSpec(body)

@OptIn(InternalAPI::class)
private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val ownUserId = UserId("alice", "server")

    val store = mockk<Store>(relaxed = true)

    afterTest {
        clearAllMocks()
    }

    context(::keyBackupCanBeTrusted.name) {
        val (privateKey, publicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
        val roomKeyVersion = GetRoomKeysVersionResponse.V1(
            authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                publicKey = Key.Curve25519Key(null, publicKey),
                signatures = mapOf(ownUserId to keysOf(Key.Ed25519Key("DEVICE", "s1"), Key.Ed25519Key("MSK", "s2")))
            ),
            count = 1,
            etag = "etag",
            version = "1"
        )

        fun deviceKeyTrustLevel(level: KeySignatureTrustLevel) {
            coEvery { store.keys.getDeviceKeys(ownUserId) } returns mapOf(
                "DEVICE" to mockk {
                    every { trustLevel } returns level
                }
            )
        }

        fun masterKeyTrustLevel(level: KeySignatureTrustLevel) {
            coEvery { store.keys.getCrossSigningKeys(ownUserId) } returns setOf(
                StoredCrossSigningKeys(
                    SignedCrossSigningKeys(
                        CrossSigningKeys(
                            ownUserId, setOf(), keysOf(Key.Ed25519Key("MSK", "msk_pub"))
                        ),
                        mapOf()
                    ),
                    level
                )
            )
        }
        should("return false, when private key is invalid") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            keyBackupCanBeTrusted(roomKeyVersion, "dino", ownUserId, store) shouldBe false
        }
        should("return false, when key backup version not supported") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            keyBackupCanBeTrusted(
                GetRoomKeysVersionResponse.Unknown(
                    JsonObject(mapOf()),
                    RoomKeyBackupAlgorithm.Unknown("")
                ), "dino", ownUserId, store
            ) shouldBe false
        }
        should("return false, when public key does not match") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                freeAfter(OlmPkDecryption.create(null)) { it.privateKey },
                ownUserId,
                store
            ) shouldBe false
        }
//        should("return false, when there is no signature we trust") {
//            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
//            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
//            keyBackupCanBeTrusted(
//                roomKeyVersion,
//                privateKey,
//                ownUserId,
//                store
//            ) shouldBe false
//        }
        should("return true, when there is a device key is valid+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(true))
            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                privateKey,
                ownUserId,
                store
            ) shouldBe true
        }
        should("return true, when there is a device key is crossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
            masterKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                privateKey,
                ownUserId,
                store
            ) shouldBe true
        }
        should("return true, when there is a master key we crossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            masterKeyTrustLevel(KeySignatureTrustLevel.CrossSigned(true))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                privateKey,
                ownUserId,
                store
            ) shouldBe true
        }
        should("return true, when there is a master key we notFullyCrossSigned+verified") {
            deviceKeyTrustLevel(KeySignatureTrustLevel.Valid(false))
            masterKeyTrustLevel(KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true))
            keyBackupCanBeTrusted(
                roomKeyVersion,
                privateKey,
                ownUserId,
                store
            ) shouldBe true
        }
    }
}