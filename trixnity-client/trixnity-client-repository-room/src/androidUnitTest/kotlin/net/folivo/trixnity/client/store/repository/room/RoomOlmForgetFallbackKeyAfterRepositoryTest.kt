package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomOlmForgetFallbackKeyAfterRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomOlmForgetFallbackKeyAfterRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomOlmForgetFallbackKeyAfterRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        repo.save(1, Instant.fromEpochMilliseconds(24))
        repo.get(1) shouldBe Instant.fromEpochMilliseconds(24)
        repo.save(1, Instant.fromEpochMilliseconds(2424))
        repo.get(1) shouldBe Instant.fromEpochMilliseconds(2424)
        repo.delete(1)
        repo.get(1) shouldBe null
    }
}
