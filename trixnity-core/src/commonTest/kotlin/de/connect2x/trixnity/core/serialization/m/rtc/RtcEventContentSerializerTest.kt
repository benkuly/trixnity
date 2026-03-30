package de.connect2x.trixnity.core.serialization.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.rtc.CallApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcSlotEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.MessageEventSerializer
import de.connect2x.trixnity.core.serialization.events.StateEventSerializer
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

@OptIn(MSC4143::class, MSC4354::class)
class RtcEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    private val rtcSlotJson =
        """
        {
          "content":{
            "application":{
              "m.call.id":"00000000-0000-0000-0000-000000000000",
              "type":"m.call"
            }
          },
          "event_id":"$123",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "state_key":"m.call#ROOM",
          "type":"m.rtc.slot",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

    private val rtcSlotJsonUnstable =
        """
        {
          "content":{
            "application":{
              "m.call.id":"00000000-0000-0000-0000-000000000000",
              "type":"m.call"
            }
          },
          "event_id":"$123",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "state_key":"m.call#ROOM",
          "type":"org.matrix.msc4143.rtc.slot",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

    private val rtcSlotEvent =
        StateEvent(
            content = RtcSlotEventContent(
                application = CallApplication(callId = "00000000-0000-0000-0000-000000000000")
            ),
            id = EventId("$123"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            stateKey = "m.call#ROOM",
            unsigned = UnsignedStateEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcSlotStateEvent() {
        json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotJson
        ) shouldBe rtcSlotEvent
    }

    @Test
    fun shouldSerializeRtcSlotStateEvent() {
        json.encodeToString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotEvent
        ) shouldBe rtcSlotJsonUnstable
    }

    @Test
    fun shouldDeserializeRtcSlotStateEventUnstable() {
        json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            rtcSlotJsonUnstable
        ) shouldBe rtcSlotEvent
    }

    private val rtcMemberJson =
        """
        {
          "content":{
            "application":{
              "m.call.id":"00000000-0000-0000-0000-000000000000",
              "type":"m.call"
            },
            "m.relates_to":{
              "event_id":"$125",
              "rel_type":"m.reference"
            },
            "member":{
              "claimed_device_id":"DEVICEID",
              "claimed_user_id":"@alice:example.org",
              "id":"xyzABCDEF0123"
            },
            "rtc_transports":[
              {
                "type":"livekit_multi_sfu"
              }
            ],
            "slot_id":"m.call#ROOM",
            "sticky_key":"xyzABCDEF0123",
            "versions":[
              "v0"
            ]
          },
          "event_id":"$126",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "type":"m.rtc.member",
          "unsigned":{
            "age":123
          }
        }
        """.trimToFlatJson()

    private val rtcMemberJsonUnstable =
        """
        {
          "content":{
            "application":{
              "m.call.id":"00000000-0000-0000-0000-000000000000",
              "type":"m.call"
            },
            "m.relates_to":{
              "event_id":"$125",
              "rel_type":"m.reference"
            },
            "member":{
              "claimed_device_id":"DEVICEID",
              "claimed_user_id":"@alice:example.org",
              "id":"xyzABCDEF0123"
            },
            "msc4354_sticky_key":"xyzABCDEF0123",
            "rtc_transports":[
              {
                "type":"livekit_multi_sfu"
              }
            ],
            "slot_id":"m.call#ROOM",
            "versions":[
              "v0"
            ]
          },
          "event_id":"$126",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "type":"org.matrix.msc4143.rtc.member",
          "unsigned":{
            "age":123
          }
        }
        """.trimToFlatJson()

    private val rtcMemberEvent =
        MessageEvent(
            content = RtcMemberEventContent(
                slotId = "m.call#ROOM",
                application = CallApplication(callId = "00000000-0000-0000-0000-000000000000"),
                member = RtcMemberEventContent.Member(
                    id = "xyzABCDEF0123",
                    claimedDeviceId = "DEVICEID",
                    claimedUserId = UserId("alice", "example.org"),
                ),
                relatesTo = RelatesTo.Reference(EventId("$125")),
                rtcTransports = listOf(RtcMemberEventContent.RtcTransport("livekit_multi_sfu")),
                stickyKey = "xyzABCDEF0123",
                versions = listOf("v0"),
            ),
            id = EventId("$126"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedMessageEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcMemberMessageEvent() {
        json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberJson
        ) shouldBe rtcMemberEvent
    }

    @Test
    fun shouldSerializeRtcMemberMessageEvent() {
        json.encodeToString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberEvent
        ) shouldBe rtcMemberJsonUnstable
    }

    @Test
    fun shouldDeserializeRtcMemberMessageEventUnstable() {
        json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberJsonUnstable
        ) shouldBe rtcMemberEvent
    }
}
