package net.folivo.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.ClockMock
import net.folivo.trixnity.client.getInMemoryUserPresenceStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
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