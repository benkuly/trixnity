package net.folivo.trixnity.examples

import kotlin.test.Test

class SendMessageTest {
    @Test
    fun shouldSendMessage() = runBlockingTest {
        SendMessage().sendMessage()
    }
}