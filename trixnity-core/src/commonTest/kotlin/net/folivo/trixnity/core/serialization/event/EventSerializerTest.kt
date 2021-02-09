package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.UnsignedData
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.UnknownMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializerTest {

    private val json = createJson()

    @Test
    fun shouldSerializeStateEvent() {
        val content: Event<StateEventContent> = StateEvent(
            CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
            EventId("143273582443PhrSn", "example.org"),
            UserId("example", "example.org"),
            1432735824653,
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            UnsignedData(1234),
            ""
        )
        val expectedResult = """
        {
            "content":{
                "alias":"#somewhere:example.org"
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234},
            "state_key":"",
            "type":"m.room.canonical_alias"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeStateEvent() {
        val input = """
        {
            "type":"m.room.canonical_alias",
            "content":{
                "alias":"#somewhere:example.org",
                "alt_aliases":[]
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234,"redactedBecause":null,"transaction_id":null},
            "state_key":"",
            "prev_content":null
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString<StateEvent<out StateEventContent>>(input)
        assertEquals(
            StateEvent(
                CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                1432735824653,
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                UnsignedData(1234),
                ""
            ), result
        )
    }

    @Test
    fun shouldSerializeRoomEvent() {
        val content = RoomEvent(
            UnknownMessageEventContent("m.dino", "hello"),
            EventId("143273582443PhrSn", "example.org"),
            UserId("example", "example.org"),
            1432735824653,
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            UnsignedData(1234)
        )
        val expectedResult = """
        {
            "content":{
                "msgtype":"m.dino",
                "body":"hello"
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234},
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeRoomEvent() {
        val input = """
        {
            "content":{
                "msgtype":"m.dino",
                "body":"hello"
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234},
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString<RoomEvent<out RoomEventContent>>(input)
        assertEquals(
            RoomEvent(
                UnknownMessageEventContent("m.dino", "hello"),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                1432735824653,
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                UnsignedData(1234)
            ), result
        )
    }

    @Test
    fun shouldSerializeEventList() {
        val content = listOf(
            StateEvent(
                id = EventId("143273582443PhrSn", "server"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedData(),
                originTimestamp = 1234,
                sender = UserId("sender", "server"),
                content = NameEventContent(),
                stateKey = ""
            ),
            StateEvent(
                id = EventId("143273584443PhrSn", "server"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedData(),
                originTimestamp = 1234,
                sender = UserId("sender", "server"),
                stateKey = UserId("user", "server").full,
                content = MemberEventContent(membership = INVITE)
            )
        )
        val expectedResult = """
        [{
            "content":{},
            "event_id":"$143273582443PhrSn:server",
            "sender":"@sender:server",
            "origin_server_ts":1234,
            "room_id":"!room:server",
            "unsigned":{},
            "state_key":"",
            "type":"m.room.name"
        },
        {
            "content":{
                "membership":"invite"
            },
            "event_id":"$143273584443PhrSn:server",
            "sender":"@sender:server",
            "origin_server_ts":1234,
            "room_id":"!room:server",
            "unsigned":{},
            "state_key":"@user:server",
            "type":"m.room.member"
        }]
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }
}