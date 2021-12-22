package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kodein.log.LoggerFactory

class ExposedStoreFactoryTest : ShouldSpec({

    lateinit var scope: CoroutineScope
    beforeTest {
        scope = CoroutineScope(Dispatchers.IO)
    }
    afterTest {
        scope.cancel()
    }

    should("create database") {
        val database = Database.connect("jdbc:h2:mem:exposed-store-factory-test1;DB_CLOSE_DELAY=-1;")
        val cut = ExposedStoreFactory(
            database = database,
            scope = scope,
            loggerFactory = LoggerFactory.default
        )

        val store = cut.createStore(
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixJson(),
            loggerFactory = LoggerFactory.default
        )
        cut.createStore(
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixJson(),
            loggerFactory = LoggerFactory.default
        )
        val room = Room(RoomId("room", "server"))
        store.room.update(room.roomId) { room }
        store.room.get(room.roomId).value shouldBe room
    }
    should("try fix wrong schema") {
        val database = Database.connect("jdbc:h2:mem:exposed-store-factory-test2;DB_CLOSE_DELAY=-1;")
        val cut = ExposedStoreFactory(
            database = database,
            scope = scope,
            loggerFactory = LoggerFactory.default
        )

        cut.createStore(
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixJson(),
            loggerFactory = LoggerFactory.default
        )
        newSuspendedTransaction(Dispatchers.IO, database) {
            with(TransactionManager.current()) {
                exec("ALTER TABLE ROOM DROP COLUMN VALUE;")
            }
        }
        val store = cut.createStore(
            contentMappings = DefaultEventContentSerializerMappings,
            json = createMatrixJson(),
            loggerFactory = LoggerFactory.default
        )
        val room = Room(RoomId("room", "server"))
        store.room.update(room.roomId) { room }
        store.room.get(room.roomId).value shouldBe room
    }
})