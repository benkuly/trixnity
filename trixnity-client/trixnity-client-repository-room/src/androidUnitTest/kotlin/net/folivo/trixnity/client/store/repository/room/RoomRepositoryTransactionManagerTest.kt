package net.folivo.trixnity.client.store.repository.room

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRepositoryTransactionManagerTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var dao: GlobalAccountDataDao
    private lateinit var tm: RoomRepositoryTransactionManager

    @Before
    fun before() {
        db = buildTestDatabase()
        dao = db.globalAccountData() // using this table as a random example
        tm = RoomRepositoryTransactionManager(db)
    }

    @Test
    fun `Should not lock when writing`() = runTest {
        tm.writeTransaction {
            testWrite(key = "bar")
            testRead()
            tm.writeTransaction {
                testWrite(key = "foo")
                testRead()
            }
        }
    }

    @Test
    fun `Should allow simultaneous transactions when writing`() = runTest {
        val calls = 10
        val callCount = MutableStateFlow(0)
        repeat(calls) { i ->
            launch {
                callCount.value++
                tm.writeTransaction {
                    callCount.first { it == calls }
                    testWrite(key = i.toString())
                }
            }
        }
    }

    @Test
    fun `Should allow simultaneous writes`() = runTest {
        val calls = 10
        val callCount = MutableStateFlow(0)
        tm.writeTransaction {
            coroutineScope {
                repeat(calls) { i ->
                    launch {
                        callCount.value++
                        callCount.first { it == calls }
                        testWrite(key = i.toString())
                    }
                }
            }
        }
    }

    @Test
    fun `Should not lock when reading`() = runTest {
        tm.readTransaction {
            testRead()
            tm.readTransaction {
                testRead()
            }
        }
    }

    private suspend fun testRead() {
        dao.getAllByType(type = "foo")
    }

    private suspend fun testWrite(key: String) {
        val entity = RoomGlobalAccountData(type = key, key = "789xyz", event = "hello world")
        dao.insert(entity)
    }
}
