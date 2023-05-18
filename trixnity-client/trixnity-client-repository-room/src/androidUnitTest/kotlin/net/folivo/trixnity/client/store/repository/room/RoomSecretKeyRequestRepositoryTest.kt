package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.StoredSecretKeyRequest
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomSecretKeyRequestRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomSecretKeyRequestRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomSecretKeyRequestRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
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
        val secretKeyRequest2Copy =
            secretKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        repo.save(key1, secretKeyRequest1)
        repo.save(key2, secretKeyRequest2)
        repo.get(key1) shouldBe secretKeyRequest1
        repo.get(key2) shouldBe secretKeyRequest2
        repo.save(key2, secretKeyRequest2Copy)
        repo.get(key2) shouldBe secretKeyRequest2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }

    @Test
    fun `Get all`() = runTest {
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

        repo.save(key1, secretKeyRequest1)
        repo.save(key2, secretKeyRequest2)
        repo.getAll() shouldContainAll listOf(secretKeyRequest1, secretKeyRequest2)
    }
}
