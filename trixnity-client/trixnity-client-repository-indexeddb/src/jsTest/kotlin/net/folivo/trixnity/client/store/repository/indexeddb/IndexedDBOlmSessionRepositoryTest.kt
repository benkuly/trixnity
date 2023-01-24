package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.StoredOlmSession

class IndexedDBOlmSessionRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBOlmSessionRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBOlmSessionRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBOlmSessionRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        val key1 = Curve25519Key(null, "curve1")
        val key2 = Curve25519Key(null, "curve2")
        val session1 = StoredOlmSession(key1, "session1", fromEpochMilliseconds(1234), pickled = "1")
        val session2 = StoredOlmSession(key2, "session2", fromEpochMilliseconds(1234), pickled = "2")
        val session3 = StoredOlmSession(key2, "session3", fromEpochMilliseconds(1234), pickled = "2")

        rtm.writeTransaction {
            cut.save(key1, setOf(session1))
            cut.save(key2, setOf(session2))
            cut.get(key1) shouldContainExactly setOf(session1)
            cut.get(key2) shouldContainExactly setOf(session2)
            cut.save(key2, setOf(session2, session3))
            cut.get(key2) shouldContainExactly setOf(session2, session3)
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})