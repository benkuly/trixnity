package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class RoomRoomOutboxMessageRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomOutboxMessageRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomOutboxMessageRepository(
            db,
            createDefaultEventContentSerializerMappings(),
            createMatrixEventJson(),
        )
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.TextBased.Text("hi"), null)
        val message2 = RoomOutboxMessage(key2, roomId, RoomMessageEventContent.FileBased.Image("hi"), null)
        val message2Copy = message2.copy(sentAt = Instant.fromEpochMilliseconds(24))

        repo.save(key1, message1)
        repo.save(key2, message2)
        val get1 = repo.get(key1)
        assertNotNull(get1)
        get1 shouldBe message1
        val get2 = repo.get(key2)
        assertNotNull(get2)
        get2 shouldBe message2
        repo.save(key2, message2Copy)
        val get2Copy = repo.get(key2)
        assertNotNull(get2Copy)
        get2Copy shouldBe message2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }

    @Test
    fun `Get all`() = runTest {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.TextBased.Text("hi"), null)
        val message2 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.FileBased.Image("hi"), null)

        repo.save(key1, message1)
        repo.save(key2, message2)
        repo.getAll() shouldHaveSize 2
    }
}
