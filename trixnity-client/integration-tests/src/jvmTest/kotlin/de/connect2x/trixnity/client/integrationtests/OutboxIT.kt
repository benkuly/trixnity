package de.connect2x.trixnity.client.integrationtests

import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.user.canSendEvent
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
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
import kotlin.time.Duration.Companion.seconds

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
        val repositoriesModule = RepositoriesModule.exposed(database)

        client = MatrixClient.create(
            repositoriesModule = repositoriesModule,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user", password)
            }.getOrThrow()
        ).getOrThrow()
        client.startSync()
        client.syncState.firstWithTimeout { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client.close()
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
            client.user.canSendEvent<RoomMessageEventContent>(room).firstWithTimeout() shouldBe true

            withCluePrintln("send messages") {
                repeat(30) {
                    client.room.sendMessage(room) { text("message $it") }
                }
            }
            withCluePrintln("wait for sent") {
                client.room.getOutbox().flatten()
                    .firstWithTimeout(60.seconds) { outbox -> outbox.none { it.sentAt != null } }
            }
            withCluePrintln("wait for empty outbox") {
                client.room.getOutbox().flatten().firstWithTimeout(90.seconds) { it.isEmpty() }
            }
            client.closeSuspending()

            val exposedRoomOutbox = object : Table("room_outbox_2") {
                val transactionId = varchar("transaction_id", length = 255)
                val roomId = varchar("roomId", length = 255)
                override val primaryKey = PrimaryKey(roomId, transactionId)
            }
            withCluePrintln("check empty database") {
                newSuspendedTransaction(Dispatchers.IO, database) {
                    exposedRoomOutbox.selectAll().map { it[exposedRoomOutbox.transactionId] } shouldBe emptyList()
                }
            }
        }
    }
}