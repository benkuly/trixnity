package de.connect2x.trixnity.core.serialization.m.rtc

import de.connect2x.trixnity.core.MSC4143
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
import de.connect2x.trixnity.core.serialization.events.RtcMemberEventContentSerializer
import de.connect2x.trixnity.core.serialization.events.RtcSlotEventContentSerializer
import de.connect2x.trixnity.core.serialization.events.StateEventSerializer
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.invoke
import de.connect2x.trixnity.core.serialization.events.messageOf
import de.connect2x.trixnity.core.serialization.events.stateOf
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(MSC4143::class)
class RtcEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    private val unstableMappings = EventContentSerializerMappings {
        @OptIn(MSC4143::class)
        messageOf<RtcMemberEventContent>("org.matrix.msc4143.rtc.member", RtcMemberEventContentSerializer())
        @OptIn(MSC4143::class)
        stateOf<RtcSlotEventContent>("org.matrix.msc4143.rtc.slot", RtcSlotEventContentSerializer())
    }

    private val slotApplication = CallApplication(callId = "00000000-0000-0000-0000-000000000000")

    private val memberApplication = CallApplication()

    @Test
    fun shouldDeserializeRtcSlotStateEventStable() {
        val input = """
        {
          "type":"m.rtc.slot",
          "content":{
            "application":{
              "type":"m.call",
              "m.call.id":"00000000-0000-0000-0000-000000000000"
            }
          },
          "event_id":"$123",
          "sender":"@alice:example.org",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "state_key":"m.call#ROOM",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

        val result = json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            input
        )

        assertIs<RtcSlotEventContent>(result.content)
        assertEquals("m.call#ROOM", result.stateKey)
        assertIs<CallApplication>(result.content.application)
        assertEquals("00000000-0000-0000-0000-000000000000", result.content.application.callId)
    }

    @Test
    fun shouldSerializeRtcSlotStateEventStable() {
        val content = StateEvent(
            content = RtcSlotEventContent(application = slotApplication),
            id = EventId("$123"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedStateEventData(age = 123),
            stateKey = "m.call#ROOM",
        )
        val result = json.encodeToString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            content
        )

        val resultJson = json.decodeFromString<JsonElement>(result).jsonObject
        assertEquals("m.rtc.slot", resultJson["type"]?.jsonPrimitive?.content)
        assertEquals("$123", resultJson["event_id"]?.jsonPrimitive?.content)
        assertEquals(1L, resultJson["origin_server_ts"]?.jsonPrimitive?.content?.toLong())
        assertEquals("!room:example.org", resultJson["room_id"]?.jsonPrimitive?.content)
        assertEquals("@alice:example.org", resultJson["sender"]?.jsonPrimitive?.content)
        assertEquals("m.call#ROOM", resultJson["state_key"]?.jsonPrimitive?.content)
        assertEquals(123L, resultJson["unsigned"]?.jsonObject?.get("age")?.jsonPrimitive?.content?.toLong())

        val application = resultJson["content"]?.jsonObject?.get("application")?.jsonObject
        assertEquals("m.call", application?.get("type")?.jsonPrimitive?.content)
        assertEquals(
            "00000000-0000-0000-0000-000000000000",
            application?.get("m.call.id")?.jsonPrimitive?.content
        )
    }

    @Test
    fun shouldDeserializeRtcSlotStateEventUnstable() {
        val input = """
        {
          "type":"org.matrix.msc4143.rtc.slot",
          "content":{},
          "event_id":"$124",
          "sender":"@alice:example.org",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "state_key":"m.call#ROOM",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

        val result = json.decodeFromString(
            StateEventSerializer(EventContentSerializerMappings.default.state),
            input
        )

        assertIs<RtcSlotEventContent>(result.content)
        assertEquals(null, result.content.application)
    }

    @Test
    fun shouldSerializeRtcSlotStateEventUnstable() {
        val content = StateEvent(
            content = RtcSlotEventContent(application = slotApplication),
            id = EventId("$124"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedStateEventData(age = 123),
            stateKey = "m.call#ROOM",
        )
        val result = json.encodeToString(
            StateEventSerializer(unstableMappings.state),
            content
        )

        val resultJson = json.decodeFromString<JsonElement>(result).jsonObject
        assertEquals("org.matrix.msc4143.rtc.slot", resultJson["type"]?.jsonPrimitive?.content)
        assertEquals("$124", resultJson["event_id"]?.jsonPrimitive?.content)
        assertEquals(1L, resultJson["origin_server_ts"]?.jsonPrimitive?.content?.toLong())
        assertEquals("!room:example.org", resultJson["room_id"]?.jsonPrimitive?.content)
        assertEquals("@alice:example.org", resultJson["sender"]?.jsonPrimitive?.content)
        assertEquals("m.call#ROOM", resultJson["state_key"]?.jsonPrimitive?.content)
        assertEquals(123L, resultJson["unsigned"]?.jsonObject?.get("age")?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun shouldDeserializeRtcMemberMessageEventStable() {
        val input = """
        {
          "type":"m.rtc.member",
          "content":{
            "slot_id":"m.call#ROOM",
            "application":{"type":"m.call"},
            "member":{
              "id":"xyzABCDEF0123",
              "claimed_device_id":"DEVICEID",
              "claimed_user_id":"@alice:example.org"
            },
            "m.relates_to":{
              "rel_type":"m.reference",
              "event_id":"$125"
            },
            "rtc_transports":[{"type":"livekit_multi_sfu"}],
            "sticky_key":"xyzABCDEF0123",
            "versions":["v0"]
          },
          "event_id":"$126",
          "sender":"@alice:example.org",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

        val result = json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            input
        )

        assertIs<RtcMemberEventContent>(result.content)
        assertEquals("m.call#ROOM", result.content.slotId)
        assertEquals("xyzABCDEF0123", result.content.member?.id)
        assertEquals("DEVICEID", result.content.member?.claimedDeviceId)
        assertEquals(UserId("@alice:example.org"), result.content.member?.claimedUserId)
        assertIs<RelatesTo.Reference>(result.content.relatesTo)
        assertEquals("livekit_multi_sfu", result.content.rtcTransports?.get(0)?.type)
        assertEquals("xyzABCDEF0123", result.content.stickyKey)
    }

    @Test
    fun shouldSerializeRtcMemberMessageEventStable() {
        val content = MessageEvent(
            content = RtcMemberEventContent(
                slotId = "m.call#ROOM",
                application = memberApplication,
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
        val result = json.encodeToString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            content
        )

        val resultJson = json.decodeFromString<JsonElement>(result).jsonObject
        assertEquals("m.rtc.member", resultJson["type"]?.jsonPrimitive?.content)
        assertEquals("$126", resultJson["event_id"]?.jsonPrimitive?.content)
        assertEquals(1L, resultJson["origin_server_ts"]?.jsonPrimitive?.content?.toLong())
        assertEquals("!room:example.org", resultJson["room_id"]?.jsonPrimitive?.content)
        assertEquals("@alice:example.org", resultJson["sender"]?.jsonPrimitive?.content)
        assertEquals(123L, resultJson["unsigned"]?.jsonObject?.get("age")?.jsonPrimitive?.content?.toLong())

        val contentJson = resultJson["content"]?.jsonObject
        assertEquals("m.call#ROOM", contentJson?.get("slot_id")?.jsonPrimitive?.content)
        assertEquals("xyzABCDEF0123", contentJson?.get("sticky_key")?.jsonPrimitive?.content)

        val application = contentJson?.get("application")?.jsonObject
        assertEquals("m.call", application?.get("type")?.jsonPrimitive?.content)

        val member = contentJson?.get("member")?.jsonObject
        assertEquals("xyzABCDEF0123", member?.get("id")?.jsonPrimitive?.content)
        assertEquals("DEVICEID", member?.get("claimed_device_id")?.jsonPrimitive?.content)
        assertEquals("@alice:example.org", member?.get("claimed_user_id")?.jsonPrimitive?.content)

        val relatesTo = contentJson?.get("m.relates_to")?.jsonObject
        assertEquals("m.reference", relatesTo?.get("rel_type")?.jsonPrimitive?.content)
        assertEquals("$125", relatesTo?.get("event_id")?.jsonPrimitive?.content)

        val rtcTransports = contentJson?.get("rtc_transports")?.jsonArray
        assertEquals("livekit_multi_sfu", rtcTransports?.get(0)?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun shouldDeserializeRtcMemberMessageEventUnstable() {
        val input = """
        {
          "type":"org.matrix.msc4143.rtc.member",
          "content":{
            "slot_id":"m.call#ROOM",
            "sticky_key":"xyzABCDEF0123"
          },
          "event_id":"$127",
          "sender":"@alice:example.org",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

        val result = json.decodeFromString(
            MessageEventSerializer(EventContentSerializerMappings.default.message),
            input
        )

        assertIs<RtcMemberEventContent>(result.content)
        assertEquals("xyzABCDEF0123", result.content.stickyKey)
    }

    @Test
    fun shouldSerializeRtcMemberMessageEventUnstable() {
        val content = MessageEvent(
            content = RtcMemberEventContent(
                slotId = "m.call#ROOM",
                stickyKey = "xyzABCDEF0123",
            ),
            id = EventId("$127"),
            sender = UserId("alice", "example.org"),
            roomId = RoomId("!room:example.org"),
            originTimestamp = 1,
            unsigned = UnsignedMessageEventData(age = 123),
        )
        val expectedResult = """
        {
          "content":{
          "slot_id":"m.call#ROOM",
          "sticky_key":"xyzABCDEF0123"
          },
          "event_id":"$127",
          "origin_server_ts":1,
          "room_id":"!room:example.org",
          "sender":"@alice:example.org",
          "type":"org.matrix.msc4143.rtc.member",
          "unsigned":{"age":123}
        }
        """.trimToFlatJson()

        val result = json.encodeToString(
            MessageEventSerializer(unstableMappings.message),
            content
        )
        assertEquals(expectedResult, result)
    }
}
