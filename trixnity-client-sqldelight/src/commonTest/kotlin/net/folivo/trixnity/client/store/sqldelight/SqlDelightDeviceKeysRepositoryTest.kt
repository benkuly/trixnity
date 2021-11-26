package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.keysOf
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightDeviceKeysRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightDeviceKeysRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut =
            SqlDelightDeviceKeysRepository(Database(driver).deviceKeysQueries, createMatrixJson(), Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        val aliceDeviceKeys = mapOf(
            "ADEV1" to DeviceKeys(
                alice,
                "ADEV1",
                setOf(Megolm, Olm),
                keysOf(Curve25519Key(null, "aliceCurveKey1"), Key.Ed25519Key(null, "aliceEdKey1")),
            ),
            "ADEV2" to DeviceKeys(
                alice,
                "ADEV2",
                setOf(Megolm, Olm),
                keysOf(Curve25519Key(null, "aliceCurveKey2"), Key.Ed25519Key(null, "aliceEdKey2")),
            )
        )
        val bobDeviceKeys = mapOf(
            "BDEV1" to DeviceKeys(
                alice,
                "BDEV1",
                setOf(Megolm, Olm),
                keysOf(Curve25519Key(null, "bobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
            )
        )
        val bobDeviceKeysCopy = mapOf(
            "BDEV1" to DeviceKeys(
                alice,
                "BDEV1",
                setOf(Megolm, Olm),
                keysOf(Curve25519Key(null, "changedBobCurveKey1"), Key.Ed25519Key(null, "bobEdKey1")),
            )
        )
        cut.save(alice, aliceDeviceKeys)
        cut.save(bob, bobDeviceKeys)
        cut.get(alice) shouldBe aliceDeviceKeys
        cut.get(bob) shouldBe bobDeviceKeys
        cut.save(bob, bobDeviceKeysCopy)
        cut.get(bob) shouldBe bobDeviceKeysCopy
        cut.delete(alice)
        cut.get(alice) shouldBe null
    }
})