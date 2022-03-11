package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.client.store.UploadCache
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedUploadMediaRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedUploadMediaRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedUploadMedia)
        }
        cut = ExposedUploadMediaRepository()
    }
    should("save, get and delete") {
        val key1 = "uri1"
        val key2 = "uri2"
        val uploadCache1 = UploadCache(key1, "mxcUri1", ContentType.Text.Plain.toString())
        val uploadCache2 = UploadCache(key2, null, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = uploadCache2.copy(mxcUri = "mxcUri2")

        newSuspendedTransaction {
            cut.save(key1, uploadCache1)
            cut.save(key2, uploadCache2)
            cut.get(key1) shouldBe uploadCache1
            cut.get(key2) shouldBe uploadCache2
            cut.save(key2, uploadMedia2Copy)
            cut.get(key2) shouldBe uploadMedia2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})