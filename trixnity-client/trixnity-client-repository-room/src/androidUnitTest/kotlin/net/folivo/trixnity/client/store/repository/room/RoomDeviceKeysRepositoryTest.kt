package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDeviceKeysRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomDeviceKeysRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomDeviceKeysRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        val aliceDeviceKeys = mapOf(
            "ADEV1" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "ADEV1",
                        setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                        keysOf(Key.Curve25519Key(null, "aliceCurveKey1"), Key.Ed25519Key(null, "aliceEdKey1")),
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
            "ADEV2" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        alice,
                        "ADEV2",
                        setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                        keysOf(Key.Curve25519Key(null, "aliceCurveKey2"), Key.Ed25519Key(null, "aliceEdKey2")),
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
                        setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                        keysOf(Key.Curve25519Key(null, "bobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
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
                        setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                        keysOf(Key.Curve25519Key(null, "changedBobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
                    ), mapOf(UserId("bob", "server") to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            )
        )

        repo.save(alice, aliceDeviceKeys)
        repo.save(bob, bobDeviceKeys)
        repo.get(alice) shouldBe aliceDeviceKeys
        repo.get(bob) shouldBe bobDeviceKeys
        repo.save(bob, bobDeviceKeysCopy)
        repo.get(bob) shouldBe bobDeviceKeysCopy
        repo.delete(alice)
        repo.get(alice) shouldBe null
    }
}
