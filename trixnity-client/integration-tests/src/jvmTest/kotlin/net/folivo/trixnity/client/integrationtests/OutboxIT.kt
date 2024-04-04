package net.folivo.trixnity.client.integrationtests

import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.media.InMemoryMediaStore
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user.canSendEvent
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class OutboxIT {

    private lateinit var client: MatrixClient
    private lateinit var database: Database

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        deleteDbFiles()
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database = newDatabase()
        val repositoriesModule = createExposedRepositoriesModule(database)

        client = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule,
            mediaStore = InMemoryMediaStore(),
            getLoginInfo = { it.register("user", password) }
        ).getOrThrow()
        client.startSync()
        client.syncState.first { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        runBlocking {
            client.stop()
        }
        deleteDbFiles()
    }

    private fun deleteDbFiles() {
        File("outbox-it.db").delete()
        File("outbox-it.mv.db").delete()
        File("outbox-it.trace.db").delete()
    }

    @Test
    fun shouldSendManyMessagesAndHaveEmptyOutboxAfterThat(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(180_000) {
            val room = client.api.room.createRoom().getOrThrow()
            client.user.canSendEvent<RoomMessageEventContent>(room).first() shouldBe true

            repeat(30) {
                client.room.sendMessage(room) { text("message $it") }
            }

            client.room.getOutbox().flattenValues().first { outbox -> outbox.none { it.sentAt != null } }
            client.room.getOutbox().flattenValues().first { it.isEmpty() }
            client.stop()

            val exposedRoomOutbox = object : Table("room_outbox") {
                val transactionId = varchar("transaction_id", length = 65535)
                override val primaryKey = PrimaryKey(transactionId)
            }
            newSuspendedTransaction(Dispatchers.IO, database) {
                exposedRoomOutbox.selectAll().map { it[exposedRoomOutbox.transactionId] } shouldBe emptyList()
            }
        }
    }
}