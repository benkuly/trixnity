package net.folivo.trixnity.client.store.repository.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.repository.sqldelight.testutils.createDriverWithSchema

class SqlDelightOlmAccountRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightOlmAccountRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightOlmAccountRepository(Database(driver).olmQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        cut.save(1, "olm")
        cut.get(1) shouldBe "olm"
        cut.save(1, "newOlm")
        cut.get(1) shouldBe "newOlm"
        cut.delete(1)
        cut.get(1) shouldBe null
    }
})