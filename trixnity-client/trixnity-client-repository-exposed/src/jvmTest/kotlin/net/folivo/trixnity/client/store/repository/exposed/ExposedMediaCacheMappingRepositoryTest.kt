package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.client.store.MediaCacheMapping
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedMediaCacheMappingRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedMediaCacheMappingRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedMediaCacheMapping)
        }
        cut = ExposedMediaCacheMappingRepository()
    }
    should("save, get and delete") {
        val key1 = "uri1"
        val key2 = "uri2"
        val mediaCacheMapping1 = MediaCacheMapping(key1, "mxcUri1", 2, ContentType.Text.Plain.toString())
        val mediaCacheMapping2 = MediaCacheMapping(key2, null, 3, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = mediaCacheMapping2.copy(mxcUri = "mxcUri2")

        newSuspendedTransaction {
            cut.save(key1, mediaCacheMapping1)
            cut.save(key2, mediaCacheMapping2)
            cut.get(key1) shouldBe mediaCacheMapping1
            cut.get(key2) shouldBe mediaCacheMapping2
            cut.save(key2, uploadMedia2Copy)
            cut.get(key2) shouldBe uploadMedia2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})