package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm

class SqlDelightVerifiedKeysRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightVerifiedKeysRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightVerifiedKeysRepository(Database(driver).deviceKeysQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val verifiedKey1Key = VerifiedKeysRepositoryKey(
            userId = UserId("alice", "server"),
            deviceId = "AAAAA",
            keyId = "key1",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        val verifiedKey2Key = VerifiedKeysRepositoryKey(
            userId = UserId("alice", "server"),
            deviceId = null,
            keyId = "key2",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        cut.save(verifiedKey1Key, "keyValue1")
        cut.save(verifiedKey2Key, "keyValue2")
        cut.get(verifiedKey1Key) shouldBe "keyValue1"
        cut.get(verifiedKey2Key) shouldBe "keyValue2"
        cut.save(verifiedKey1Key, "keyValue1Changed")
        cut.get(verifiedKey1Key) shouldBe "keyValue1Changed"
        cut.delete(verifiedKey1Key)
        cut.get(verifiedKey1Key) shouldBe null
    }
})