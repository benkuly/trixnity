package net.folivo.trixnity.client.store.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.UploadMedia
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.sqldelight.testutils.createDriverWithSchema

class SqlDelightUploadMediaRepositoryTest : ShouldSpec({
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
        val uploadMedia1 = UploadMedia(key1, "mxcUri1", ContentType.Text.Plain.toString())
        val uploadMedia2 = UploadMedia(key2, null, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = uploadMedia2.copy(mxcUri = "mxcUri2")

        cut.save(key1, uploadMedia1)
        cut.save(key2, uploadMedia2)
        cut.get(key1) shouldBe uploadMedia1
        cut.get(key2) shouldBe uploadMedia2
        cut.save(key2, uploadMedia2Copy)
        cut.get(key2) shouldBe uploadMedia2Copy
        cut.delete(key1)
        cut.get(key1) shouldBe null
    }
})