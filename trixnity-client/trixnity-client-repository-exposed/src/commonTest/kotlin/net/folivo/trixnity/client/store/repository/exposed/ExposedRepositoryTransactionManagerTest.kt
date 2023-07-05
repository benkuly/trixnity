package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private object TestEntity : LongIdTable("test_entity") {
    val value = text("value")
}

private suspend fun testWrite() = withExposedWrite {
    TestEntity.insert {
        it[value] = "test"
    }
}

private suspend fun testRead() = withExposedRead {
    TestEntity.selectAll().toList()
}

class ExposedRepositoryTransactionManagerTest : ShouldSpec({
    timeout = 5_000

    lateinit var tm: ExposedRepositoryTransactionManager
    beforeTest {
        val db = createDatabase()
        newSuspendedTransaction(Dispatchers.IO, db) {
            SchemaUtils.create(TestEntity)
            TestEntity.deleteAll()
        }
        tm = ExposedRepositoryTransactionManager(db)
    }
    context("writeTransaction") {
        should("not lock") {
            tm.writeTransaction {
                testWrite()
                testRead()
                tm.writeTransaction {
                    testWrite()
                    testRead()
                }
            }
        }
    }

    context("readTransaction") {
        should("not lock") {
            tm.readTransaction {
                testRead()
                tm.readTransaction {
                    testRead()
                }
            }
        }
    }

    should("handle massive parallel read and write") {
        tm.writeTransaction {
            coroutineScope {
                repeat(200) {
                    launch {
                        testWrite()
                    }
                    launch {
                        testRead()
                    }
                }
            }
        }
    }

    context("rollback") {
        should("rollback on exception") {
            try {
                tm.writeTransaction {
                    testWrite()
                    throw RuntimeException("dino")
                }
            } catch (_: Exception) {
            }
            tm.writeTransaction {
                testWrite()
            }
            tm.readTransaction {
                testRead()
            }.size shouldBe 1
        }
    }
})
