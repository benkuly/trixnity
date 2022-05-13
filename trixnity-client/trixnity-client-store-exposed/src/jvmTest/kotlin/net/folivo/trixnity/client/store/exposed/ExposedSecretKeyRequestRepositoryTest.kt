package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.SecretKeyRequestRepository
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedSecretKeyRequestRepositoryTest : ShouldSpec({
    lateinit var cut: SecretKeyRequestRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedSecretKeyRequest)
        }
        cut = ExposedSecretKeyRequestRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val key1 = "key1"
        val key2 = "key2"
        val secretKeyRequest1 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val secretKeyRequest2 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("2", KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )
        val secretKeyRequest2Copy = secretKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        newSuspendedTransaction {
            cut.save(key1, secretKeyRequest1)
            cut.save(key2, secretKeyRequest2)
            cut.get(key1) shouldBe secretKeyRequest1
            cut.get(key2) shouldBe secretKeyRequest2
            cut.save(key2, secretKeyRequest2Copy)
            cut.get(key2) shouldBe secretKeyRequest2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("get all") {
        val key1 = "key1"
        val key2 = "key2"
        val secretKeyRequest1 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("1", KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val secretKeyRequest2 = StoredSecretKeyRequest(
            SecretKeyRequestEventContent("2", KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )

        newSuspendedTransaction {
            cut.save(key1, secretKeyRequest1)
            cut.save(key2, secretKeyRequest2)
            cut.getAll() shouldContainAll listOf(secretKeyRequest1, secretKeyRequest2)
        }
    }
})