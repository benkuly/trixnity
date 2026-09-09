package de.connect2x.trixnity.core.serialization.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import de.connect2x.trixnity.core.model.events.m.rtc.CallRtcApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberEventContent
import de.connect2x.trixnity.core.model.events.m.rtc.RtcMemberId
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.MessageEventSerializer
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(MSC4143::class, MSC4354::class, MSC4193::class)
class RtcMemberEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    private val rtcMemberJoinJson =
        """
        {
          "content": {
            "application": {
              "type": "m.call"
            },
            "member": {
              "id": "{member_id}",
              "membership": "join"
            },
            "msc4354_sticky_key": "{member_id}",
            "slot_id": "m.call#room",
            "transports": {
              "can_subscribe": [
                "{transport_type}"
              ],
              "published": [
                {
                  "type": "{transport_type}"
                }
              ]
            }
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
        """
            .trimToFlatJson()

    private val rtcMemberJoinEvent =
        MessageEvent(
            content =
                RtcMemberEventContent.Join(
                    slotId = CallRtcApplication.SLOT_ID,
                    application = CallRtcApplication.Member(),
                    member = RtcMemberEventContent.Member(id = RtcMemberId("{member_id}")),
                    transports =
                        RtcMemberEventContent.RtcTransports(
                            listOf(
                                RtcTransport.Unknown(
                                    "{transport_type}",
                                    buildJsonObject { put("type", "{transport_type}") },
                                )
                            ),
                            listOf("{transport_type}"),
                        ),
                    stickyKey = "{member_id}",
                ),
            id = EventId("$126"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedMessageEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcMemberJoinEvent() {
        json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberJoinJson,
        ) shouldBe rtcMemberJoinEvent
    }

    @Test
    fun shouldSerializeRtcMemberJoinEvent() {
        json.encodeToString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberJoinEvent,
        ) shouldBe rtcMemberJoinJson
    }

    private val rtcMemberLeaveJson =
        """
        {
          "content": {
            "leave_reason": {
              "code":"err",
              "reason":"blub"
            },
            "member": {
              "id": "{member_id}",
              "membership": "leave"
            },
            "msc4354_sticky_key": "{member_id}",
            "slot_id": "m.call#room"
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
        """
            .trimToFlatJson()

    private val rtcMemberLeaveEvent =
        MessageEvent(
            content =
                RtcMemberEventContent.Leave(
                    slotId = CallRtcApplication.SLOT_ID,
                    member = RtcMemberEventContent.Member(id = RtcMemberId("{member_id}")),
                    reason = RtcMemberEventContent.Leave.Reason("err", "blub"),
                    stickyKey = "{member_id}",
                ),
            id = EventId("$126"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedMessageEventData(age = 123),
        )

    @Test
    fun shouldDeserializeRtcMemberLeaveEvent() {
        json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberLeaveJson,
        ) shouldBe rtcMemberLeaveEvent
    }

    @Test
    fun shouldSerializeRtcMemberLeaveEvent() {
        json.encodeToString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            rtcMemberLeaveEvent,
        ) shouldBe rtcMemberLeaveJson
    }
}
