package net.folivo.trixnity.core.model.events.m.room

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import kotlin.test.Test

class RoomMessageEventContentTest {

    @Test
    fun shouldNotStripFallbackWhenTheMessageIsNoReply() {
        val cut = RoomMessageEventContent.TextMessageEventContent(
            body = "> Hello World!",
        )

        cut.bodyWithoutFallback shouldBe cut.body
    }

    @Test
    fun shouldNotStripFallbackOfReplyIfNoFallbackIsProvided() {
        val cut = RoomMessageEventContent.TextMessageEventContent(
            body = "Hello World!",
            relatesTo = RelatesTo.Reply(replyTo = RelatesTo.ReplyTo(EventId("1")))
        )

        cut.bodyWithoutFallback shouldBe cut.body
    }

    @Test
    fun shouldStripFallbackOfReply() {
        val cut = RoomMessageEventContent.TextMessageEventContent(
            body = "> Hello World!\n> FooBar\n\nMy answer.",
            relatesTo = RelatesTo.Reply(replyTo = RelatesTo.ReplyTo(EventId("1")))
        )

        cut.bodyWithoutFallback shouldBe "My answer."
    }

    @Test
    fun shouldNotStripLinesAfterTheFallback() {
        val cut = RoomMessageEventContent.TextMessageEventContent(
            body = "> Hello World!\n> FooBar\n\n> I want to be seen.",
            relatesTo = RelatesTo.Reply(replyTo = RelatesTo.ReplyTo(EventId("1")))
        )

        cut.bodyWithoutFallback shouldBe "> I want to be seen."
    }
}