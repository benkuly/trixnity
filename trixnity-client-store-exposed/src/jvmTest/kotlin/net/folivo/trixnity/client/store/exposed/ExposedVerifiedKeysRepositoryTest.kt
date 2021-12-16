package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.verification.KeyVerificationState.Blocked
import net.folivo.trixnity.client.verification.KeyVerificationState.Verified
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedVerifiedKeysRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedKeyVerificationStateRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedKeyVerificationState)
        }
        cut = ExposedKeyVerificationStateRepository(createMatrixJson())
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

        newSuspendedTransaction {
            cut.save(verifiedKey1Key, Verified("keyValue1"))
            cut.save(verifiedKey2Key, Blocked("keyValue2"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValue1")
            cut.get(verifiedKey2Key) shouldBe Blocked("keyValue2")
            cut.save(verifiedKey1Key, Verified("keyValueChanged"))
            cut.get(verifiedKey1Key) shouldBe Verified("keyValueChanged")
            cut.delete(verifiedKey1Key)
            cut.get(verifiedKey1Key) shouldBe null
        }
    }
})