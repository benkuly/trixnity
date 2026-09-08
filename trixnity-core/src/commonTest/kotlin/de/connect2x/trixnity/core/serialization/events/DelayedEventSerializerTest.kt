package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.DelayedEvent
import de.connect2x.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(MSC4140::class)
class DelayedEventSerializerTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    val delayedStateEvent =
        DelayedEvent.DelayedStateEvent(
            content = CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
            delayId = "delay123",
            roomId = RoomId("!jEsUZKDJdhlrceRyVU:example.org"),
            stateKey = "",
            delayMs = 1000,
            delayedSinceTs = 987654321,
            finalized = DelayedEvent.Finalized.Success(123, EventId("eventId")),
        )
    val serializedScheduledDelayedStateEvent =
        """{
          "content": {
            "alias":"#somewhere:example.org"
          },
          "delay_id": "delay123",
          "delay_ms": 1000,
          "delayed_since_ts":987654321,
          "finalized":{"event_id":"eventId","finalised_ts":123},
          "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
          "state_key": "",
          "type": "m.room.canonical_alias"
        }"""
            .trimToFlatJson()

    @Test
    fun shouldSerializeScheduledDelayedStateEvent() {
        val result =
            json.encodeToString(
                DelayedStateEventSerializer(EventContentSerializerMappings.default.state),
                delayedStateEvent,
            )
        result shouldBe serializedScheduledDelayedStateEvent
    }

    @Test
    fun shouldDeserializeDelayedStateEvent() {
        val result =
            json.decodeFromString(
                DelayedStateEventSerializer(EventContentSerializerMappings.default.state),
                serializedScheduledDelayedStateEvent,
            )
        assertEquals(delayedStateEvent, result)
    }

    val scheduledDelayedMessageEvent =
        DelayedEvent.DelayedMessageEvent(
            content = RoomMessageEventContent.TextBased.Text("Hello world!"),
            delayId = "delay456",
            roomId = RoomId("!anotherRoom:example.org"),
            delayMs = 2000,
            delayedSinceTs = 987654321,
            finalized = DelayedEvent.Finalized.Error(123, ErrorResponse.Forbidden("nonono")),
        )
    val serializedScheduledDelayedMessageEvent =
        """{
          "content": {
            "body": "Hello world!",
            "msgtype": "m.text"
          },
          "delay_id": "delay456",
          "delay_ms": 2000,
          "delayed_since_ts":987654321,
          "finalized":{"error":{"errcode":"M_FORBIDDEN","error":"nonono"},"finalised_ts":123},
          "room_id": "!anotherRoom:example.org",
          "type": "m.room.message"
        }"""
            .trimToFlatJson()

    @Test
    fun shouldSerializeScheduledDelayedMessageEvent() {
        val result =
            json.encodeToString(
                DelayedMessageEventSerializer(EventContentSerializerMappings.default.message),
                scheduledDelayedMessageEvent,
            )
        result shouldBe serializedScheduledDelayedMessageEvent
    }

    @Test
    fun shouldDeserializeScheduledDelayedMessageEvent() {
        val result =
            json.decodeFromString(
                DelayedMessageEventSerializer(EventContentSerializerMappings.default.message),
                serializedScheduledDelayedMessageEvent,
            )
        assertEquals(scheduledDelayedMessageEvent, result)
    }
}
