package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOutdatedKeysRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomOutdatedKeysRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomOutdatedKeysRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")

        repo.save(1, setOf(alice))
        repo.get(1) shouldContainExactly setOf(alice)
        repo.save(1, setOf(alice, bob))
        repo.get(1) shouldContainExactly setOf(alice, bob)
        repo.delete(1)
        repo.get(1) shouldBe null
    }
}
