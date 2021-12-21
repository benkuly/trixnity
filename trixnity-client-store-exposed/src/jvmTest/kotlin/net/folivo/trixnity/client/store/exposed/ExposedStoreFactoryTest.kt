package net.folivo.trixnity.client.store.exposed

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kodein.log.LoggerFactory
import javax.sql.DataSource

class ExposedStoreFactoryTest : ShouldSpec({

    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.IO)
    }
    afterTest {
        scope.cancel()
    }

    context("migration") {
        should("migrate and check schema") {
            val dataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:exposed-store-factory-test1")
            }
            val cut = ExposedStoreFactory(
                dataSource = dataSource,
                scope = scope,
                loggerFactory = LoggerFactory.default
            )

            cut.createStore(
                contentMappings = DefaultEventContentSerializerMappings,
                json = createMatrixJson(),
                loggerFactory = LoggerFactory.default
            )

            cut.createStore(
                contentMappings = DefaultEventContentSerializerMappings,
                json = createMatrixJson(),
                loggerFactory = LoggerFactory.default
            )
        }
        should("find wrong schema") {
            val dataSource: DataSource = JdbcDataSource().apply {
                setURL("jdbc:h2:mem:exposed-store-factory-test2")
            }
            val cut = ExposedStoreFactory(
                dataSource = dataSource,
                scope = scope,
                loggerFactory = LoggerFactory.default
            )
            cut.createStore(
                contentMappings = DefaultEventContentSerializerMappings,
                json = createMatrixJson(),
                loggerFactory = LoggerFactory.default
            )
            val exposedDatabase = Database.connect(dataSource)
            newSuspendedTransaction(Dispatchers.IO, exposedDatabase) {
                with(TransactionManager.current()) {
                    exec("ALTER TABLE ROOM DROP COLUMN VALUE;")
                }
            }
            shouldThrow<ExposedMigrationCheckException> {
                cut.createStore(
                    contentMappings = DefaultEventContentSerializerMappings,
                    json = createMatrixJson(),
                    loggerFactory = LoggerFactory.default
                ).room.update(RoomId("room", "server")) { Room(RoomId("room", "server")) }
            }
        }
    }
})