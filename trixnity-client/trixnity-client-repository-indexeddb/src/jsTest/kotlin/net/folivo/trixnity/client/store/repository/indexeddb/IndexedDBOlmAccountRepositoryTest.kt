package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBOlmAccountRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBOlmAccountRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBOlmAccountRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBOlmAccountRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        rtm.writeTransaction {
            cut.save(1, "olm")
            cut.get(1) shouldBe "olm"
            cut.save(1, "newOlm")
            cut.get(1) shouldBe "newOlm"
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})