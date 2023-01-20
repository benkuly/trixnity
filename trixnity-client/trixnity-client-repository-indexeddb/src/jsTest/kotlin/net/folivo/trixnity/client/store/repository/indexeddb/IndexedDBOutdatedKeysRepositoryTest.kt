package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBOutdatedKeysRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBOutdatedKeysRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBOutdatedKeysRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBOutdatedKeysRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
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