package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.key.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedDeviceKeysRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: ExposedDeviceKeysRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedDeviceKeys)
        }
        cut = ExposedDeviceKeysRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        val aliceDeviceKeys = mapOf(
            "ADEV1" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "ADEV1",
                        setOf(Megolm, Olm),
                        keysOf(Curve25519Key(null, "aliceCurveKey1"), Key.Ed25519Key(null, "aliceEdKey1")),
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
            "ADEV2" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "ADEV2",
                        setOf(Megolm, Olm),
                        keysOf(Curve25519Key(null, "aliceCurveKey2"), Key.Ed25519Key(null, "aliceEdKey2")),
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            )
        )
        val bobDeviceKeys = mapOf(
            "BDEV1" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "BDEV1",
                        setOf(Megolm, Olm),
                        keysOf(Curve25519Key(null, "bobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            )
        )
        val bobDeviceKeysCopy = mapOf(
            "BDEV1" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "BDEV1",
                        setOf(Megolm, Olm),
                        keysOf(Curve25519Key(null, "changedBobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
                    ), mapOf(UserId("bob", "server") to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            )
        )
        newSuspendedTransaction {
            cut.save(alice, aliceDeviceKeys)
            cut.save(bob, bobDeviceKeys)
            cut.get(alice) shouldBe aliceDeviceKeys
            cut.get(bob) shouldBe bobDeviceKeys
            cut.save(bob, bobDeviceKeysCopy)
            cut.get(bob) shouldBe bobDeviceKeysCopy
            cut.delete(alice)
            cut.get(alice) shouldBe null
        }
    }
})