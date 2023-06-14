package net.folivo.trixnity.client.integrationtests

import io.kotest.assertions.withClue
import io.ktor.http.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.room
import net.folivo.trixnity.client.room.getAccountData
import net.folivo.trixnity.client.room.getState
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import net.folivo.trixnity.client.user
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@Testcontainers
class RoomListIT {

    private lateinit var startedClient: StartedClient

    @Container
    val synapseDocker = synapseDocker()

    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        val baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient =
            registerAndStartClient("client1", "user1", baseUrl, createExposedRepositoriesModule(newDatabase()))
    }

    @AfterTest
    fun afterEach() {
        startedClient.scope.cancel()
    }

    @Test
    fun testForgetRoom(): Unit = runBlocking {
        withTimeout(90_000) {
            val roomId = startedClient.client.api.rooms.createRoom().getOrThrow()

            var lastEventId: EventId? = null
            withClue("room should exist locally") {
                startedClient.client.room.getAll().first { it.containsKey(roomId) }
                startedClient.client.room.getState<CreateEventContent>(roomId).first { it != null }
                startedClient.client.user.getAll(roomId).first { it.isNullOrEmpty().not() }
                lastEventId =
                    startedClient.client.room.getLastTimelineEvent(roomId).filterNotNull().first().filterNotNull()
                        .first().eventId
                startedClient.client.api.rooms.setReadMarkers(roomId, lastEventId).getOrThrow()
                startedClient.client.room.getAccountData<FullyReadEventContent>(roomId).first { it != null }
            }

            startedClient.client.api.rooms.leaveRoom(roomId).getOrThrow()
            startedClient.client.api.rooms.forgetRoom(roomId).getOrThrow()

            // force synapse to send a new room list
            delay(2.seconds)
            startedClient.client.api.rooms.createRoom().getOrThrow()

            withClue("room should be deleted locally") {
                startedClient.client.room.getById(roomId).first { it == null }
                startedClient.client.room.getAll().first { it[roomId]?.value == null }
                startedClient.client.room.getState<CreateEventContent>(roomId).first { it == null }
                startedClient.client.user.getAll(roomId)
                    .first { users -> users?.all { user -> user.value.first { it == null } == null } == true }
                startedClient.client.room.getTimelineEvent(roomId, requireNotNull(lastEventId))
                    .first { it == null }
                startedClient.client.room.getAccountData<FullyReadEventContent>(roomId).first { it == null }
            }
        }
    }
}