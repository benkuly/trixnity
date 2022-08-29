package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.client.store.KeyVerificationState.Blocked
import net.folivo.trixnity.client.store.KeyVerificationState.Verified
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class SqlDelightVerifiedKeysRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightKeyVerificationStateRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightKeyVerificationStateRepository(
            Database(driver).keysQueries,
            createMatrixEventJson(),
            Dispatchers.Default
        )
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val verifiedKey1Key = VerifiedKeysRepositoryKey(
            keyId = "key1",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        val verifiedKey2Key = VerifiedKeysRepositoryKey(
            keyId = "key2",
            keyAlgorithm = KeyAlgorithm.Ed25519
        )
        cut.save(verifiedKey1Key, Verified("keyValue1"))
        cut.save(verifiedKey2Key, Blocked("keyValue2"))
        cut.get(verifiedKey1Key) shouldBe Verified("keyValue1")
        cut.get(verifiedKey2Key) shouldBe Blocked("keyValue2")
        cut.save(verifiedKey1Key, Verified("keyValueChanged"))
        cut.get(verifiedKey1Key) shouldBe Verified("keyValueChanged")
        cut.delete(verifiedKey1Key)
        cut.get(verifiedKey1Key) shouldBe null
    }
})