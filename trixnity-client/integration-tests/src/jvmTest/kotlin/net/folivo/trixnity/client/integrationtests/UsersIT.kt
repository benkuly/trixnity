package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.loginWith
import net.folivo.trixnity.client.media.createInMemoryMediaStoreModule
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.store.membership
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import org.jetbrains.exposed.sql.Database
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@Testcontainers
class UsersIT {

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

        val repositoriesModule1 = createExposedRepositoriesModule(database1)
        val repositoriesModule2 = createExposedRepositoriesModule(database2)

        client1 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule1,
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user1", password) }
        ).getOrThrow()
        client2 = MatrixClient.loginWith(
            baseUrl = baseUrl,
            repositoriesModule = repositoriesModule2,
            mediaStoreModule = createInMemoryMediaStoreModule(),
            getLoginInfo = { it.register("user2", password) }
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