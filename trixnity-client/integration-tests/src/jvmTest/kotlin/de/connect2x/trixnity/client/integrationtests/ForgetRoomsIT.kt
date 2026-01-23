package de.connect2x.trixnity.client.integrationtests

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.MatrixClientConfiguration.DeleteRooms
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.room.message.text
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.INVITE
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
class ForgetRoomsIT : TrixnityBaseTest() {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var database1: Database
    private lateinit var database2: Database
    private lateinit var baseUrl: Url
    val password = "user$1passw0rd"

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        database1 = newDatabase()
        database2 = newDatabase()

        val repositoriesModule1 = RepositoriesModule.exposed(database1)
        val repositoriesModule2 = RepositoriesModule.exposed(database2)

        client1 = MatrixClient.create(
            repositoriesModule = repositoriesModule1,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user1", password)
            }.getOrThrow(),
            configuration = {
                name = "client1"
            },
        ).getOrThrow()
        client2 = MatrixClient.create(
            repositoriesModule = repositoriesModule2,
            mediaStoreModule = MediaStoreModule.inMemory(),
            cryptoDriverModule = CryptoDriverModule.vodozemac(),
            authProviderData = MatrixClientAuthProviderData.classicLoginWith(baseUrl) {
                it.register("user2", password)
            }.getOrThrow(),
            configuration = {
                name = "client2"
                deleteRooms = DeleteRooms.WhenNotJoined // <--
            },
        ).getOrThrow()
        client1.startSync()
        client2.startSync()
        client1.syncState.firstWithTimeout { it == SyncState.RUNNING }
        client2.syncState.firstWithTimeout { it == SyncState.RUNNING }
    }

    @AfterTest
    fun afterEach() {
        client1.close()
        client2.close()
    }

    @Test
    fun `should delete rooms that were not joined`(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                initialState = listOf(
                    InitialStateEvent(content = EncryptionEventContent(), ""),

                    ),
            ).getOrThrow()

            withCluePrintln("Client1 creates a room and writes messages") {
                client1.room.getById(room).firstWithTimeout { it?.encrypted == true }
                client1.room.sendMessage(room) { text("Hello!") }
                client1.room.waitForOutboxSent()
                client1.room.sendMessage(room) { text("Anybody?") }
                client1.room.waitForOutboxSent()
                client1.api.room.inviteUser(room, client2.userId).getOrThrow()
                // the next line we cannot activate (synapse bug?)
                // client1.room.sendMessage(room) { text("Come on!") }
            }

            withCluePrintln("client2 sees invitation") {
                client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            }

            withCluePrintln("client1 kicks client2") {
                delay(300.milliseconds)
                client1.api.room.kickUser(room, client2.userId, "does not respond").getOrThrow()
            }

            withCluePrintln("client2 does not see the room after kicking") {
                client2.room.getById(room).firstWithTimeout { it == null }
            }
        }
    }
}