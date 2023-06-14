package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOlmSessionRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomOlmSessionRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomOlmSessionRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = Key.Curve25519Key(null, "curve1")
        val key2 = Key.Curve25519Key(null, "curve2")
        val session1 =
            StoredOlmSession(key1, "session1", Instant.fromEpochMilliseconds(1234), pickled = "1")
        val session2 =
            StoredOlmSession(key2, "session2", Instant.fromEpochMilliseconds(1234), pickled = "2")
        val session3 =
            StoredOlmSession(key2, "session3", Instant.fromEpochMilliseconds(1234), pickled = "2")

        repo.save(key1, setOf(session1))
        repo.save(key2, setOf(session2))
        repo.get(key1) shouldContainExactly setOf(session1)
        repo.get(key2) shouldContainExactly setOf(session2)
        repo.save(key2, setOf(session2, session3))
        repo.get(key2) shouldContainExactly setOf(session2, session3)
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }
}
