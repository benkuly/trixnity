package net.folivo.trixnity.client.room

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.TimelineEvent
import kotlin.test.Test

class TimelineBuilderUtilsTest {

    @Test
    fun shouldCreateTimeline() {
        val event0 = plainEvent(0)
        val event1 = plainEvent(1)
        val event2 = plainEvent(2)
        val event3 = plainEvent(3)
        val event4 = plainEvent(4)
        val event5 = plainEvent(5)
        val event6 = plainEvent(6)
        val event7 = plainEvent(7)
        timeline {
            fragment {
                +event0
                +event1
                +event2
                gap("2after")
            }
            fragment {
                gap("3both1")
                +event3
                gap("3both2")
                gap("4before")
                +event4
                +event5
            }
            fragment {
                +event6
                gap("6to7")
                +event7
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
                gap = TimelineEvent.Gap.after("2after")
            ),
            TimelineEvent(
                event = event3,
                previousEventId = null,
                nextEventId = event4.id,
                gap = TimelineEvent.Gap.both("3both1", "3both2")
            ),
            TimelineEvent(
                event = event4,
                previousEventId = event3.id,
                nextEventId = event5.id,
                gap = TimelineEvent.Gap.before("4before")
            ),
            TimelineEvent(
                event = event5,
                previousEventId = event4.id,
                nextEventId = null,
                gap = null
            ),
            TimelineEvent(
                event = event6,
                previousEventId = null,
                nextEventId = event7.id,
                gap = TimelineEvent.Gap.after("6to7")
            ),
            TimelineEvent(
                event = event7,
                previousEventId = event6.id,
                nextEventId = null,
                gap = TimelineEvent.Gap.before("6to7")
            ),
        )
    }
}