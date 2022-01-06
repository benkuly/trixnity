package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedAccountRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedAccountRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedAccount)
        }
        cut = ExposedAccountRepository()
    }
    should("save, get and delete") {
        val account = Account(
            "",
            Url("http://host"),
            UserId("alice", "server"),
            "aliceDevice",
            "accessToken",
            "syncToken",
            "filterId",
            "displayName",
            Url("mxc://localhost/123456"),
        )
        newSuspendedTransaction {
            cut.save(1, account)
            cut.get(1) shouldBe account
            val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
            cut.save(1, accountCopy)
            cut.get(1) shouldBe accountCopy
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})