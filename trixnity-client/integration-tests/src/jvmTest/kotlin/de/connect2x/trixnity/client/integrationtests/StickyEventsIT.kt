package de.connect2x.trixnity.client.integrationtests

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.client.room.getAllSticky
import de.connect2x.trixnity.client.store.membership
import de.connect2x.trixnity.client.store.repository.exposed.exposed
import de.connect2x.trixnity.client.user
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Testcontainers
@MSC3814
class StickyEventsIT : TrixnityBaseTest() {
    private lateinit var startedClient1: StartedClient
    private lateinit var startedClient2: StartedClient

    private val scope = CoroutineScope(Dispatchers.Default)

    @Container
    val synapseDocker = synapseDocker()

    lateinit var baseUrl: Url

    @OptIn(MSC4354::class)
    @BeforeTest
    fun beforeEach(): Unit = runBlocking {
        baseUrl = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = synapseDocker.host,
            port = synapseDocker.firstMappedPort
        ).build()
        startedClient1 = registerAndStartClient(
            "client1", "user1", baseUrl,
            RepositoriesModule.exposed(newDatabase())
        ) {
            experimentalFeatures.enableMSC4354 = true
        }
        startedClient2 = registerAndStartClient(
            "client2", "user2", baseUrl,
            RepositoriesModule.exposed(newDatabase())
        ) {
            experimentalFeatures.enableMSC4354 = true
        }
    }

    @AfterTest
    fun afterEach() {
        startedClient1.client.close()
        startedClient2.client.close()
        scope.cancel()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, MSC4143::class, MSC4354::class)
    @Test
    fun sendAndRetrieveStickyEvents(): Unit = runBlocking(Dispatchers.Default) {
        withTimeout(30.seconds) {
            val roomId = startedClient1.client.api.room.createRoom(
                invite = setOf(startedClient2.client.userId),
                initialState = listOf(InitialStateEvent(content = EncryptionEventContent(), ""))
            ).getOrThrow()
            startedClient2.client.api.room.joinRoom(roomId).getOrThrow()

            startedClient1.client.user.getById(roomId, startedClient2.client.userId)
                .firstWithTimeout { it?.membership == Membership.JOIN }

            val stickyContent = RtcMemberEventContent("device", "slot")
            startedClient1.client.room.sendMessage(roomId, stickyDuration = 1.minutes) {
                content(RtcMemberEventContent("device", "slot"))
            }

            startedClient2.client.room.getAllSticky<RtcMemberEventContent>(roomId)
                .flattenValues()
                .firstWithTimeout { it.firstOrNull()?.content == stickyContent }
        }
    }
}
