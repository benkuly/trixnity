package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.OutdatedKeysRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.UserId
import org.koin.core.Koin


fun ShouldSpec.outdatedKeysRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: OutdatedKeysRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("outdatedKeysRepositoryTest: save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")

        rtm.writeTransaction {
            cut.save(1, setOf(alice))
            cut.get(1) shouldContainExactly setOf(alice)
            cut.save(1, setOf(alice, bob))
            cut.get(1) shouldContainExactly setOf(alice, bob)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}