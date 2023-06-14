package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomInboundMegolmMessageIndexRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomInboundMegolmMessageIndexRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomInboundMegolmMessageIndexRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val roomId = RoomId("room", "server")
        val messageIndexKey1 = InboundMegolmMessageIndexRepositoryKey("session1", roomId, 24)
        val messageIndexKey2 = InboundMegolmMessageIndexRepositoryKey("session2", roomId, 12)
        val messageIndex1 = StoredInboundMegolmMessageIndex(
            "session1", roomId, 24,
            EventId("event"),
            1234
        )
        val messageIndex2 = StoredInboundMegolmMessageIndex(
            "session2", roomId, 12,
            EventId("event"),
            1234
        )
        val messageIndex2Copy = messageIndex2.copy(originTimestamp = 1235)

        repo.save(messageIndexKey1, messageIndex1)
        repo.save(messageIndexKey2, messageIndex2)
        repo.get(messageIndexKey1) shouldBe messageIndex1
        repo.get(messageIndexKey2) shouldBe messageIndex2
        repo.save(messageIndexKey2, messageIndex2Copy)
        repo.get(messageIndexKey2) shouldBe messageIndex2Copy
        repo.delete(messageIndexKey1)
        repo.get(messageIndexKey1) shouldBe null
    }
}
