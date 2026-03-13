package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class EventContentSerializerMappingsTest {

    @Test
    fun plusShouldMergeMappings() {
        val mappings1 = EventContentSerializerMappings {
            messageOf<RoomMessageEventContent.TextBased.Text>("m.room.message")
            stateOf<MemberEventContent>("m.room.member")
        }
        val mappings2 = EventContentSerializerMappings {
            ephemeralOf<TypingEventContent>("m.typing")
        }

        val result = mappings1 + mappings2

        result.message.shouldHaveSize(1)
        result.message.first().type.shouldBe("m.room.message")
        result.state.shouldHaveSize(1)
        result.state.first().type.shouldBe("m.room.member")
        result.ephemeral.shouldHaveSize(1)
        result.ephemeral.first().type.shouldBe("m.typing")
    }

    @Test
    fun plusShouldOverrideExistingMappings() {
        val mappings1 = EventContentSerializerMappings {
            messageOf<RoomMessageEventContent.TextBased.Text>("m.room.message")
        }
        val mappings2 = EventContentSerializerMappings {
            messageOf<RoomMessageEventContent.TextBased.Notice>("m.room.message")
        }

        val result = mappings1 + mappings2

        result.message.shouldHaveSize(1)
        result.message.first().type.shouldBe("m.room.message")
        result.message.first().kClass.shouldBe(RoomMessageEventContent.TextBased.Notice::class)
    }

    @Test
    fun minusShouldRemoveMappings() {
        val mappings1 = EventContentSerializerMappings {
            messageOf<RoomMessageEventContent.TextBased.Text>("m.room.message")
            stateOf<MemberEventContent>("m.room.member")
        }
        val mappingsToRemove = EventContentSerializerMappings {
            messageOf<RoomMessageEventContent.TextBased.Text>("m.room.message")
        }

        val result = mappings1 - mappingsToRemove

        result.message.shouldHaveSize(0)
        result.state.shouldHaveSize(1)
        result.state.first().type.shouldBe("m.room.member")
    }
}
