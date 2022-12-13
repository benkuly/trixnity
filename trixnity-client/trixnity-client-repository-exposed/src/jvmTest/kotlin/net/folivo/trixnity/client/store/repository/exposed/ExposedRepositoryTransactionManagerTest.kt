package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private object TestEntity : LongIdTable("test_entity") {
    val value = text("value")
}

private fun testWrite() = TestEntity.insert {
    it[value] = "test"
}

private fun testRead() = TestEntity.selectAll().toList()
class ExposedRepositoryTransactionManagerTest : ShouldSpec({
    timeout = 1_000

    lateinit var tm: ExposedRepositoryTransactionManager
    beforeTest {
        val db = createDatabase()
        newSuspendedTransaction(Dispatchers.IO, db) {
            SchemaUtils.create(TestEntity)
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
})
