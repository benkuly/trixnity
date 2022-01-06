package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.UserId

class SqlDelightAccountRepositoryTest : ShouldSpec({
    lateinit var cut: SqlDelightAccountRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightAccountRepository(Database(driver).accountQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
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
        cut.save(1, account)
        cut.get(1) shouldBe account
        val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
        cut.save(1, accountCopy)
        cut.get(1) shouldBe accountCopy
        cut.delete(1)
        cut.get(1) shouldBe null
    }
})