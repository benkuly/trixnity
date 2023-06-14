package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.core.model.UserId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomAccountRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomAccountRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomAccountRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val account = Account(
            olmPickleKey = "",
            baseUrl = "http://host",
            userId = UserId("alice", "server"),
            deviceId = "aliceDevice",
            accessToken = "accessToken",
            syncBatchToken = "syncToken",
            filterId = "filterId",
            backgroundFilterId = "backgroundFilterId",
            displayName = "displayName",
            avatarUrl = "mxc://localhost/123456",
        )

        repo.save(1, account)
        repo.get(1) shouldBe account
        val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
        repo.save(1, accountCopy)
        repo.get(1) shouldBe accountCopy
        repo.delete(1)
        repo.get(1) shouldBe null
    }
}
