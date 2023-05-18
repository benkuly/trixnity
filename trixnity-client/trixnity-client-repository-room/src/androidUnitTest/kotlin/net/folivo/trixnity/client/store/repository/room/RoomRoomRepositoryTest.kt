package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key2, lastEventId = null)
        val room2Copy = room2.copy(lastEventId = EventId("\$Event2"))

        repo.save(key1, room1)
        repo.save(key2, room2)
        repo.get(key1) shouldBe room1
        repo.get(key2) shouldBe room2
        repo.save(key2, room2Copy)
        repo.get(key2) shouldBe room2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }

    @Test
    fun `Get all`() = runTest {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key1, lastEventId = null)

        repo.save(key1, room1)
        repo.save(key2, room2)
        repo.getAll() shouldContainAll listOf(room1, room2)
    }
}
