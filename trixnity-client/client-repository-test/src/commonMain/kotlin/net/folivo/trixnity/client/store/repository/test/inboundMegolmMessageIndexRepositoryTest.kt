package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import net.folivo.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import org.koin.core.Koin


fun ShouldSpec.inboundMegolmMessageIndexRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: InboundMegolmMessageIndexRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("inboundMegolmMessageIndexRepositoryTest: save, get and delete") {
        val roomId = RoomId("room", "server")
        val messageIndexKey1 =
            InboundMegolmMessageIndexRepositoryKey("session1", roomId, 24)
        val messageIndexKey2 =
            InboundMegolmMessageIndexRepositoryKey("session2", roomId, 12)
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

        rtm.writeTransaction {
            cut.save(messageIndexKey1, messageIndex1)
            cut.save(messageIndexKey2, messageIndex2)
            cut.get(messageIndexKey1) shouldBe messageIndex1
            cut.get(messageIndexKey2) shouldBe messageIndex2
            cut.save(messageIndexKey2, messageIndex2Copy)
            cut.get(messageIndexKey2) shouldBe messageIndex2Copy
            cut.delete(messageIndexKey1)
            cut.get(messageIndexKey1) shouldBe null
        }
    }
}