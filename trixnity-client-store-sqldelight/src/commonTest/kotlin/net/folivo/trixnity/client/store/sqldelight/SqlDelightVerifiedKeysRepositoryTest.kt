package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.client.verification.KeyVerificationState.Blocked
import net.folivo.trixnity.client.verification.KeyVerificationState.Verified
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixJson

class SqlDelightVerifiedKeysRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightKeyVerificationStateRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightKeyVerificationStateRepository(
            Database(driver).keysQueries,
            createMatrixJson(),
            Dispatchers.Default
        )
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