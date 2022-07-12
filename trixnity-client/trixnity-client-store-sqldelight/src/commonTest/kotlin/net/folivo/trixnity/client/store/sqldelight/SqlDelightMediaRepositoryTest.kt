package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema

class SqlDelightMediaRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightMediaRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightMediaRepository(Database(driver).mediaQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        cut.save("url1", "test1".toByteArray())
        cut.save("url2", "test2".toByteArray())
        cut.get("url1") shouldBe "test1".toByteArray()
        cut.get("url2") shouldBe "test2".toByteArray()
        cut.save("url2", "test2Copy".toByteArray())
        cut.get("url2") shouldBe "test2Copy".toByteArray()
        cut.delete("url1")
        cut.get("url1") shouldBe null
    }
    should("change url") {
        cut.save("url3", "test3".toByteArray())
        cut.changeUri("url3", "url4")
        cut.get("url3") shouldBe null
        cut.get("url4") shouldBe "test3".toByteArray()
    }
})