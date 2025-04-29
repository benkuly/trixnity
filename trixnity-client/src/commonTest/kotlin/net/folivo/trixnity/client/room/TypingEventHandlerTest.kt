package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class TypingEventHandlerTest : TrixnityBaseTest() {
    private val room1 = RoomId("room1", "localhost")
    private val room2 = RoomId("room2", "localhost")

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