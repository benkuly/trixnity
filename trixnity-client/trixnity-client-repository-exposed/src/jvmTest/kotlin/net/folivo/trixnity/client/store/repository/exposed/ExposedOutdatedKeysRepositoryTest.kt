package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedOutdatedKeysRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedOutdatedKeysRepository
    lateinit var rtm: ExposedRepositoryTransactionManager

    beforeTest {
        val db = createDatabase()
        rtm = ExposedRepositoryTransactionManager(db)
        newSuspendedTransaction {
            SchemaUtils.create(ExposedOutdatedKeys)
        }
        cut = ExposedOutdatedKeysRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")

        rtm.writeTransaction {
            cut.save(1, setOf(alice))
            cut.get(1) shouldContainExactly setOf(alice)
            cut.save(1, setOf(alice, bob))
            cut.get(1) shouldContainExactly setOf(alice, bob)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})