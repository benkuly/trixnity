package net.folivo.trixnity.client.room

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class TypingEventHandlerTest : ShouldSpec({
    timeout = 5_000
    val room1 = RoomId("room1", "localhost")
    val room2 = RoomId("room2", "localhost")

    lateinit var cut: TypingEventHandler

    val json = createMatrixEventJson()
    beforeTest {
        cut = TypingEventHandler(
            mockMatrixClientServerApiClient(json).first,
        )
    }

    context("setTyping") {
        should("set the presence for a user whose presence is not known") {
            val typingEventContent = TypingEventContent(setOf(UserId("bla")))
            cut.usersTyping.value[room1] shouldBe null
            cut.setTyping(Event.EphemeralEvent(typingEventContent, roomId = room1))
            cut.usersTyping.value[room1] shouldBe typingEventContent
        }

        should("overwrite the typing of a room when a new status is known") {
            val typingEventContent1 = TypingEventContent(setOf(UserId("bla")))
            val typingEventContent2 = TypingEventContent(setOf(UserId("bla"), UserId("blub")))
            cut.setTyping(Event.EphemeralEvent(typingEventContent1, roomId = room2))
            cut.setTyping(Event.EphemeralEvent(typingEventContent2, roomId = room2))

            cut.usersTyping.value[room2] shouldBe typingEventContent2
        }
    }
})