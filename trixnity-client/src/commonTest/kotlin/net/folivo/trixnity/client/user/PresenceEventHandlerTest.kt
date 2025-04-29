package net.folivo.trixnity.client.user

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class PresenceEventHandlerTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "localhost")
    private val bob = UserId("bob", "localhost")

    private val cut = PresenceEventHandlerImpl(mockMatrixClientServerApiClient())

    @Test
    fun `setPresence » set the presence for a user whose presence is not known`() = runTest {
        cut.userPresence.value[alice] shouldBe null
        cut.setPresence(EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = alice))
        cut.userPresence.value[alice] shouldBe PresenceEventContent(Presence.ONLINE)
    }

    @Test
    fun `setPresence » overwrite the presence of a user when a new status is known`() = runTest {
        cut.setPresence(EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = bob))
        cut.setPresence(EphemeralEvent(PresenceEventContent(Presence.UNAVAILABLE), sender = bob))

        cut.userPresence.value[bob] shouldBe PresenceEventContent(Presence.UNAVAILABLE)
    }

}