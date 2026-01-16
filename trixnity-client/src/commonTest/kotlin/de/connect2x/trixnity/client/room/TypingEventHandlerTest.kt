package de.connect2x.trixnity.client.room

import io.kotest.matchers.shouldBe
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.EphemeralEvent
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

class TypingEventHandlerTest : TrixnityBaseTest() {
    private val room1 = RoomId("!room1:localhost")
    private val room2 = RoomId("!room2:localhost")

    private val cut = TypingEventHandlerImpl(
        mockMatrixClientServerApiClient(),
    )

    @Test
    fun `setTyping » set the presence for a user whose presence is not known`() = runTest {
        val typingEventContent = TypingEventContent(setOf(UserId("bla")))
        cut.usersTyping.value[room1] shouldBe null
        cut.setTyping(EphemeralEvent(typingEventContent, roomId = room1))
        cut.usersTyping.value[room1] shouldBe typingEventContent
    }

    @Test
    fun `setTyping » overwrite the typing of a room when a new status is known`() = runTest {
        val typingEventContent1 = TypingEventContent(setOf(UserId("bla")))
        val typingEventContent2 = TypingEventContent(setOf(UserId("bla"), UserId("blub")))
        cut.setTyping(EphemeralEvent(typingEventContent1, roomId = room2))
        cut.setTyping(EphemeralEvent(typingEventContent2, roomId = room2))

        cut.usersTyping.value[room2] shouldBe typingEventContent2
    }
}