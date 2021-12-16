package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.client.store.UploadMedia
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
        val uploadMedia1 = UploadMedia(key1, "mxcUri1", ContentType.Text.Plain)
        val uploadMedia2 = UploadMedia(key2, null, ContentType.Image.PNG)
        val uploadMedia2Copy = uploadMedia2.copy(mxcUri = "mxcUri2")

        newSuspendedTransaction {
            cut.save(key1, uploadMedia1)
            cut.save(key2, uploadMedia2)
            cut.get(key1) shouldBe uploadMedia1
            cut.get(key2) shouldBe uploadMedia2
            cut.save(key2, uploadMedia2Copy)
            cut.get(key2) shouldBe uploadMedia2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})