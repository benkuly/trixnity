package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomKeyRequestRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomKeyRequestRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomKeyRequestRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )
        val roomKeyRequest2Copy =
            roomKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        repo.save(key1, roomKeyRequest1)
        repo.save(key2, roomKeyRequest2)
        repo.get(key1) shouldBe roomKeyRequest1
        repo.get(key2) shouldBe roomKeyRequest2
        repo.save(key2, roomKeyRequest2Copy)
        repo.get(key2) shouldBe roomKeyRequest2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }

    @Test
    fun `Get all`() = runTest {
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )

        repo.save(key1, roomKeyRequest1)
        repo.save(key2, roomKeyRequest2)
        repo.getAll() shouldContainAll listOf(roomKeyRequest1, roomKeyRequest2)
    }
}
