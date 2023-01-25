package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.AutoIncrement
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic

class IndexedDBRepositoryTransactionManagerTest : ShouldSpec({
    timeout = 5_000
    @Serializable
    data class IndexedDBRepositoryTransactionManagerTestEntity(
        val something: String
    )

    class IndexedDBRepositoryTransactionManagerTestRepository : IndexedDBRepository("testStore") {
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun testWrite(): Unit = withIndexedDBWrite { store ->
            store.put(Json.encodeToDynamic(IndexedDBRepositoryTransactionManagerTestEntity("test")))
            Unit
        }

        suspend fun testRead(): List<IndexedDBRepositoryTransactionManagerTestEntity> = withIndexedDBRead { store ->
            store.getAll().mapNotNull { Json.decodeFromDynamicNullable(it) }
        }
    }

    lateinit var testRepo: IndexedDBRepositoryTransactionManagerTestRepository
    lateinit var tm: IndexedDBRepositoryTransactionManager
    beforeTest {
        testRepo = IndexedDBRepositoryTransactionManagerTestRepository()
        val db = openDatabase(uuid4().toString(), 1) { database, _, _ ->
            database.createObjectStore("testStore", autoIncrement = AutoIncrement)
        }
        tm = IndexedDBRepositoryTransactionManager(db, arrayOf("testStore"))
    }
    should("not lock on read") {
        tm.writeTransaction {
            testRepo.testWrite()
            testRepo.testRead()
            tm.writeTransaction {
                testRepo.testWrite()
                testRepo.testRead()
            }
        }
    }

    should("not lock on write") {
        tm.readTransaction {
            testRepo.testRead()
            tm.readTransaction {
                testRepo.testRead()
            }
        }
    }

    should("handle massive parallel read and write") {
        tm.writeTransaction {
            coroutineScope {
                repeat(200) {
                    launch {
                        testRepo.testWrite()
                    }
                    launch {
                        testRepo.testRead()
                    }
                }
            }
        }
    }
})
