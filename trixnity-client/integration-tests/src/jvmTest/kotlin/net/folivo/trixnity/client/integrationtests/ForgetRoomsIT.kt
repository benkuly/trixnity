package net.folivo.trixnity.client.integrationtests

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.MatrixClientConfiguration.DeleteRooms
import net.folivo.trixnity.client.cryptodriver.vodozemac.vodozemac
import net.folivo.trixnity.client.media.inMemory
import net.folivo.trixnity.client.room.message.text
import net.folivo.trixnity.client.store.repository.exposed.exposed
import net.folivo.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.classicLoginWith
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

@Testcontainers
class ForgetRoomsIT {

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

        client1 = MatrixClient.login(
            baseUrl = baseUrl,
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
        client2 = MatrixClient.login(
            baseUrl = baseUrl,
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