package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedOlmAccountRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedOlmAccountRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedOlmAccount)
        }
        cut = ExposedOlmAccountRepository()
    }
    should("save, get and delete") {
        newSuspendedTransaction {
            cut.save(1, "olm")
            cut.get(1) shouldBe "olm"
            cut.save(1, "newOlm")
            cut.get(1) shouldBe "newOlm"
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})