package net.folivo.trixnity.examples

import kotlin.test.Ignore
import kotlin.test.Test

class SendMessageTest {
    @Test
    @Ignore
    fun shouldSendMessage() = runBlockingTest {
        SendMessage().sendMessage()
    }
}