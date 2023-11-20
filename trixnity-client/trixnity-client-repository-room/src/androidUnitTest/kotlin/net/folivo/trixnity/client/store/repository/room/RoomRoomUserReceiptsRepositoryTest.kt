package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRoomUserReceiptsRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomRoomUserReceiptsRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomRoomUserReceiptsRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val userReceipt1 = RoomUserReceipts(
            key1, UserId("alice", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )
        val userReceipt2 = RoomUserReceipts(
            key1, UserId("bob", "server"), mapOf(
                ReceiptType.Unknown("bla") to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )
        val userReceipt3 = RoomUserReceipts(
            key1, UserId("cedric", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )

        repo.save(key1, userReceipt1.userId, userReceipt1)
        repo.save(key2, userReceipt2.userId, userReceipt2)
        repo.get(key1) shouldBe mapOf(userReceipt1.userId to userReceipt1)
        repo.get(key1, userReceipt1.userId) shouldBe userReceipt1
        repo.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2)
        repo.save(key2, userReceipt3.userId, userReceipt3)
        repo.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2, userReceipt3.userId to userReceipt3)
        repo.delete(key1, userReceipt1.userId)
        repo.get(key1) shouldHaveSize 0
    }

    @Test
    fun `Save and get by second key`() = runTest {
        val key = RoomId("room1", "server")
        val userReceipt = RoomUserReceipts(
            key, UserId("alice", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )

        repo.save(key, userReceipt.userId, userReceipt)
        repo.get(key, userReceipt.userId) shouldBe userReceipt
    }
}
