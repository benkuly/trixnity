package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema

class SqlDelightUploadMediaRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: SqlDelightUploadMediaRepository
    lateinit var driver: SqlDriver
    beforeTest {
        driver = createDriverWithSchema()
        cut = SqlDelightUploadMediaRepository(Database(driver).mediaQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
    }
    should("save, get and delete") {
        val key1 = "uri1"
        val key2 = "uri2"
        val uploadCache1 = UploadCache(key1, "mxcUri1", ContentType.Text.Plain.toString())
        val uploadCache2 = UploadCache(key2, null, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = uploadCache2.copy(mxcUri = "mxcUri2")

        cut.save(key1, uploadCache1)
        cut.save(key2, uploadCache2)
        cut.get(key1) shouldBe uploadCache1
        cut.get(key2) shouldBe uploadCache2
        cut.save(key2, uploadMedia2Copy)
        cut.get(key2) shouldBe uploadMedia2Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})