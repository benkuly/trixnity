package de.connect2x.trixnity.core.serialization.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.PerMemberRtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.StateEventSerializer
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

@OptIn(MSC4143::class, MSC4193::class)
class RtcSlotEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    private val rtcSlotOpenJson =
        """
        {
          "content": {
            "application": {
              "type": "m.call"
            },
            "encryption": {
              "type":"org.matrix.msc4143.per_member"
            },
            "status": "open"
          },
          "event_id":"$123",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "state_key": "{application_type}#{application_slot_id}",
          "type":"org.matrix.msc4143.rtc.slot",
          "unsigned":{"age":123}
        }
        """
            .trimToFlatJson()

    private val rtcSlotOpenEvent =
        StateEvent(
            content =
                RtcSlotEventContent.Open(application = CallRtcApplication.Slot, encryption = PerMemberRtcEncryption),
            id = EventId("$123"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            stateKey = "{application_type}#{application_slot_id}",
            unsigned = UnsignedStateEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcSlotOpenvent() {
        json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotOpenJson,
        ) shouldBe rtcSlotOpenEvent
    }

    @Test
    fun shouldSerializeRtcSlotOpenEvent() {
        json.encodeToString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotOpenEvent,
        ) shouldBe rtcSlotOpenJson
    }

    private val rtcSlotClosedJson =
        """
        {
          "content": {
            "status": "closed"
          },
          "event_id":"$123",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "state_key": "{application_type}#{application_slot_id}",
          "type":"org.matrix.msc4143.rtc.slot",
          "unsigned":{"age":123}
        }
        """
            .trimToFlatJson()

    private val rtcSlotClosedEvent =
        StateEvent(
            content = RtcSlotEventContent.Closed(),
            id = EventId("$123"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            stateKey = "{application_type}#{application_slot_id}",
            unsigned = UnsignedStateEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcSlotClosedEvent() {
        json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotClosedJson,
        ) shouldBe rtcSlotClosedEvent
    }

    @Test
    fun shouldSerializeRtcSlotClosedEvent() {
        json.encodeToString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotClosedEvent,
        ) shouldBe rtcSlotClosedJson
    }
}
