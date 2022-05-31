package net.folivo.trixnity.client

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.room.timeline
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEvent.Gap.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import kotlin.test.Test

class TimelineBuilderUtilsTest {

    private fun textEvent(i: Long = 24): Event.MessageEvent<RoomMessageEventContent.TextMessageEventContent> {
        return Event.MessageEvent(
            RoomMessageEventContent.TextMessageEventContent("message $i"),
            EventId("\$event$i"),
            UserId("sender", "server"),
            RoomId("room", "server"),
            i
        )
    }

    @Test
    fun shouldCreateTimeline() {
        val event0 = textEvent(0)
        val event1 = textEvent(1)
        val event2 = textEvent(2)
        val event3 = textEvent(3)
        val event4 = textEvent(4)
        val event5 = textEvent(5)
        val event6 = textEvent(6)
        val event7 = textEvent(7)
        timeline(RoomId("room", "server")) {
            fragment {
                event(0)
                event(1)
                event(2)
                gap("2after")
            }
            fragment {
                gap("3both1")
                event(3)
                gap("3both2")
                gap("4before")
                event(4)
            }
            fragment {
                event(5)
                gap("5to6")
                event(6)
                event(7)
            }
        } shouldBe listOf(
            TimelineEvent(
                event = event0,
                previousEventId = null,
                nextEventId = event1.id,
                gap = null
            ),
            TimelineEvent(
                event = event1,
                previousEventId = event0.id,
                nextEventId = event2.id,
                gap = null
            ),
            TimelineEvent(
                event = event2,
                previousEventId = event1.id,
                nextEventId = null,
                gap = GapAfter("2after")
            ),
            TimelineEvent(
                event = event3,
                previousEventId = null,
                nextEventId = event4.id,
                gap = GapBoth("3both2")
            ),
            TimelineEvent(
                event = event4,
                previousEventId = event3.id,
                nextEventId = null,
                gap = GapBefore("4before")
            ),
            TimelineEvent(
                event = event5,
                previousEventId = null,
                nextEventId = event6.id,
                gap = GapAfter("5to6")
            ),
            TimelineEvent(
                event = event6,
                previousEventId = event5.id,
                nextEventId = event7.id,
                gap = GapBefore("5to6")
            ),
            TimelineEvent(
                event = event7,
                previousEventId = event6.id,
                nextEventId = null,
                gap = null
            ),
        )
    }
}