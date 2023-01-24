package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBOlmForgetFallbackKeyAfterRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBOlmForgetFallbackKeyAfterRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBOlmForgetFallbackKeyAfterRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBOlmForgetFallbackKeyAfterRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
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