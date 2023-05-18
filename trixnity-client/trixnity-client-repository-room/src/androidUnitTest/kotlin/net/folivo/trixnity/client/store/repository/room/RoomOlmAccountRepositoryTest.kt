package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOlmAccountRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomOlmAccountRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomOlmAccountRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        repo.save(1, "olm")
        repo.get(1) shouldBe "olm"
        repo.save(1, "newOlm")
        repo.get(1) shouldBe "newOlm"
        repo.delete(1)
        repo.get(1) shouldBe null
    }
}
