package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.UserId
import org.koin.core.Koin

fun ShouldSpec.accountRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: AccountRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("accountRepositoryTest: save, get and delete") {
        val account = Account(
            "",
            "http://host",
            UserId("alice", "server"),
            "aliceDevice",
            "accessToken",
            "syncToken",
            "filterId",
            "backgroundFilterId",
            "displayName",
            "mxc://localhost/123456",
        )
        rtm.writeTransaction {
            cut.save(1, account)
            println(1)
            cut.get(1) shouldBe account
            println(2)
            val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
            cut.save(1, accountCopy)
            println(3)
            cut.get(1) shouldBe accountCopy
            println(4)
            cut.delete(1)
            println(5)
            cut.get(1) shouldBe null
            println(6)
        }
    }
}