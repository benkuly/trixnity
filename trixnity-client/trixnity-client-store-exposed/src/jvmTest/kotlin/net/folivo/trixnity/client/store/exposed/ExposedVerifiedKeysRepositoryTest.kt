package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.VerifiedKeysRepositoryKey
import net.folivo.trixnity.client.verification.KeyVerificationState.Blocked
import net.folivo.trixnity.client.verification.KeyVerificationState.Verified
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedVerifiedKeysRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: ExposedKeyVerificationStateRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedKeyVerificationState)
        }
        cut = ExposedKeyVerificationStateRepository(createMatrixEventJson())
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