package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.OutboundMegolmSessionRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.crypto.olm.StoredOutboundMegolmSession
import org.koin.core.Koin


fun ShouldSpec.outboundMegolmSessionRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: OutboundMegolmSessionRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("outboundMegolmSessionRepositoryTest: save, get and delete") {
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

        rtm.writeTransaction {
            cut.save(key1, session1)
            cut.save(key2, session2)
            cut.get(key1) shouldBe session1
            cut.get(key2) shouldBe session2
            cut.save(key2, session2Copy)
            cut.get(key2) shouldBe session2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
}