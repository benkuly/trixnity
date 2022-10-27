package net.folivo.trixnity.client.user

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class PresenceEventHandlerTest : ShouldSpec({
    timeout = 5_000
    val alice = UserId("alice", "localhost")
    val bob = UserId("bob", "localhost")

    lateinit var cut: PresenceEventHandler

    val json = createMatrixEventJson()
    beforeTest {
        cut = PresenceEventHandler(
            mockMatrixClientServerApiClient(json).first,
        )
    }

    context("setPresence") {
        should("set the presence for a user whose presence is not known") {
            cut.userPresence.value[alice] shouldBe null
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = alice))
            cut.userPresence.value[alice] shouldBe PresenceEventContent(Presence.ONLINE)
        }

        should("overwrite the presence of a user when a new status is known") {
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.ONLINE), sender = bob))
            cut.setPresence(Event.EphemeralEvent(PresenceEventContent(Presence.UNAVAILABLE), sender = bob))

            cut.userPresence.value[bob] shouldBe PresenceEventContent(Presence.UNAVAILABLE)
        }
    }
})