package de.connect2x.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.ClockMock
import de.connect2x.trixnity.client.getInMemoryUserPresenceStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.mocks.TransactionManagerMock
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.EphemeralEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.PresenceEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class PresenceEventHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "localhost")
    private val bob = UserId("bob", "localhost")
    private val userPresenceStore = getInMemoryUserPresenceStore()
    private val clock = ClockMock()

    private val cut =
        UserPresenceEventHandler(
            userPresenceStore,
            TransactionManagerMock(),
            clock,
            mockMatrixClientServerApiClient()
        )

    @Test
    fun `setPresence » set the presence for a user`() = runTest {
        cut.setPresence(
            listOf(
                EphemeralEvent(
                    PresenceEventContent(
                        presence = Presence.ONLINE,
                        lastActiveAgo = 24,
                        isCurrentlyActive = false,
                        statusMessage = "status"
                    ), sender = alice
                )
            )
        )
        userPresenceStore.getPresence(alice).first() shouldBe UserPresence(
            presence = Presence.ONLINE,
            lastUpdate = clock.now(),
            lastActive = clock.now() - 24.milliseconds,
            isCurrentlyActive = false,
            statusMessage = "status"
        )
    }
}