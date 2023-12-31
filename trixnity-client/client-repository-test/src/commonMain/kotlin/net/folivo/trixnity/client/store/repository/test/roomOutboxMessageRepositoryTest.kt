package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.store.RoomOutboxMessage
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomOutboxMessageRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.koin.core.Koin

fun ShouldSpec.roomOutboxMessageRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomOutboxMessageRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomOutboxMessageRepositoryTest: save, get and delete") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.TextBased.Text("hi"), null)
        val message2 = RoomOutboxMessage(key2, roomId, RoomMessageEventContent.FileBased.Image("hi"), null)
        val message2Copy = message2.copy(sentAt = fromEpochMilliseconds(24))

        rtm.writeTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            val get1 = cut.get(key1)
            get1.shouldNotBeNull()
            get1 shouldBe message1
            val get2 = cut.get(key2)
            get2.shouldNotBeNull()
            get2 shouldBe message2
            cut.save(key2, message2Copy)
            val get2Copy = cut.get(key2)
            get2Copy.shouldNotBeNull()
            get2Copy shouldBe message2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("roomOutboxMessageRepositoryTest: get all") {
        val roomId = RoomId("room", "server")
        val key1 = "transaction1"
        val key2 = "transaction2"
        val message1 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.TextBased.Text("hi"), null)
        val message2 = RoomOutboxMessage(key1, roomId, RoomMessageEventContent.FileBased.Image("hi"), null)

        rtm.writeTransaction {
            cut.save(key1, message1)
            cut.save(key2, message2)
            cut.getAll() shouldHaveSize 2
        }
    }
}