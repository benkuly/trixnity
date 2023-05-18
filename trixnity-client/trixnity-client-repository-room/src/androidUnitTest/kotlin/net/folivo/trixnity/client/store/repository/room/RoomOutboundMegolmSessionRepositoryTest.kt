package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOutboundMegolmSessionRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomOutboundMegolmSessionRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomOutboundMegolmSessionRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val session1 = StoredOutboundMegolmSession(key1, pickled = "1")
        val session2 = StoredOutboundMegolmSession(key2, pickled = "2")
        val session2Copy = session2.copy(
            newDevices = mapOf(
                UserId("bob", "server") to setOf("Device1"),
                UserId("alice", "server") to setOf("Device2", "Device3")
            )
        )

        repo.save(key1, session1)
        repo.save(key2, session2)
        repo.get(key1) shouldBe session1
        repo.get(key2) shouldBe session2
        repo.save(key2, session2Copy)
        repo.get(key2) shouldBe session2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }
}
