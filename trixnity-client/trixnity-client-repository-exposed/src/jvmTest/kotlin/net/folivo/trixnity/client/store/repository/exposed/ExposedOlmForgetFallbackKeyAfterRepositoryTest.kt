package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedOlmForgetFallbackKeyAfterRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedOlmForgetFallbackKeyAfterRepository
    lateinit var rtm: ExposedRepositoryTransactionManager

    beforeTest {
        val db = createDatabase()
        rtm = ExposedRepositoryTransactionManager(db)
        newSuspendedTransaction {
            SchemaUtils.create(ExposedOlmForgetFallbackKeyAfter)
        }
        cut = ExposedOlmForgetFallbackKeyAfterRepository()
    }
    should("save, get and delete") {
        rtm.writeTransaction {
            cut.save(1, Instant.fromEpochMilliseconds(24))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(24)
            cut.save(1, Instant.fromEpochMilliseconds(2424))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(2424)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})