package de.connect2x.trixnity.client.integrationtests

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.cryptodriver.vodozemac.vodozemac
import de.connect2x.trixnity.client.media.inMemory
import de.connect2x.trixnity.client.store.membership
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.client.classicLoginWith
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.INVITE
import de.connect2x.trixnity.core.model.events.m.room.Membership.JOIN
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class UsersIT : TrixnityBaseTest() {

    private lateinit var client1: MatrixClient
    private lateinit var client2: MatrixClient
    private lateinit var database1: Database
    private lateinit var database2: Database

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val password = "user$1passw0rd"
        val baseUrl = URLBuilder(
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
    fun shouldHaveUsersInRoom(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30_000) {
            val room = client1.api.room.createRoom(
                invite = setOf(client2.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), "")),
                isDirect = true,
            ).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == INVITE }
            client2.api.room.joinRoom(room).getOrThrow()
            client2.room.getById(room).firstWithTimeout { it?.membership == JOIN }

            client1.user.getById(room, client2.userId).firstWithTimeout { it?.membership == JOIN }
            client2.user.getById(room, client2.userId).firstWithTimeout { it?.membership == JOIN }
            assertSoftly {
                client1.user.getAll(room).filterNotNull().firstWithTimeout().keys shouldBe setOf(
                    client1.userId,
                    client2.userId
                )
                client2.user.getAll(room).filterNotNull().firstWithTimeout().keys shouldBe setOf(
                    client1.userId,
                    client2.userId
                )
            }
        }
    }
}