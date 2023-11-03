package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.RoomUserReceipts
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomUserReceiptsRepository
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import org.koin.core.Koin


fun ShouldSpec.roomUserReceiptsRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomUserReceiptsRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomUserReceiptsRepositoryTest: save, get and delete") {
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

        rtm.writeTransaction {
            cut.save(key1, userReceipt1.userId, userReceipt1)
            cut.save(key2, userReceipt2.userId, userReceipt2)
            cut.get(key1) shouldBe mapOf(userReceipt1.userId to userReceipt1)
            cut.get(key1, userReceipt1.userId) shouldBe userReceipt1
            cut.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2)
            cut.save(key2, userReceipt3.userId, userReceipt3)
            cut.get(key2) shouldBe mapOf(userReceipt2.userId to userReceipt2, userReceipt3.userId to userReceipt3)
            cut.delete(key1, userReceipt1.userId)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("roomUserReceiptsRepositoryTest: save and get by second key") {
        val key = RoomId("room1", "server")
        val userReceipt = RoomUserReceipts(
            key, UserId("alice", "server"), mapOf(
                ReceiptType.FullyRead to RoomUserReceipts.Receipt(
                    EventId("event"),
                    ReceiptEventContent.Receipt(1L)
                )
            )
        )

        rtm.writeTransaction {
            cut.save(key, userReceipt.userId, userReceipt)
            cut.get(key, userReceipt.userId) shouldBe userReceipt
        }
    }
}