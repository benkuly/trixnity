package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedMediaRepositoryTest : ShouldSpec({
    timeout = 60_000
    lateinit var cut: ExposedMediaRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedMedia)
        }
        cut = ExposedMediaRepository()
    }
    should("save, get and delete") {
        newSuspendedTransaction {
            cut.save("url1", "test1".toByteArray())
            cut.save("url2", "test2".toByteArray())
            cut.get("url1") shouldBe "test1".toByteArray()
            cut.get("url2") shouldBe "test2".toByteArray()
            cut.save("url2", "test2Copy".toByteArray())
            cut.get("url2") shouldBe "test2Copy".toByteArray()
            cut.delete("url1")
            cut.get("url1") shouldBe null
        }
    }
    should("change url") {
        newSuspendedTransaction {
            cut.save("url3", "test3".toByteArray())
            cut.changeUri("url3", "url4")
            cut.get("url3") shouldBe null
            cut.get("url4") shouldBe "test3".toByteArray()
        }
    }
})