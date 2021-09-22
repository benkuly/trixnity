package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.RedactedRoomEventContent
import net.folivo.trixnity.core.model.events.UnknownStateEventContent
import net.folivo.trixnity.core.model.events.UnsignedData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.UnknownMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.kodein.log.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializerTest {

    private val json = createMatrixJson()

    @Test
    fun shouldSerializeStateEvent() {
        val content = StateEvent(
            CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
            EventId("143273582443PhrSn", "example.org"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
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
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234},
            "state_key":"",
            "type":"m.room.canonical_alias"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
                LoggerFactory.default
            ), content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeStateEvent() {
        val input = """
        {
            "type":"m.room.canonical_alias",
            "content":{
                "alias":"#somewhere:example.org"
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
        val result = json.decodeFromString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
                LoggerFactory.default
            ), input
        )
        assertEquals(
            StateEvent(
                CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedData(1234),
                ""
            ), result
        )
    }

    @Test
    fun shouldSerializeRoomEvent() {
        val content = RoomEvent(
            MessageEventContent.TextMessageEventContent("hello"),
            EventId("143273582443PhrSn", "example.org"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedData(1234)
        )
        val expectedResult = """
        {
            "content":{
                "body":"hello",
                "msgtype":"m.text"
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234},
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            RoomEventSerializer(DefaultEventContentSerializerMappings.room, LoggerFactory.default),
            content
        )
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
        val result = json.decodeFromString(
            RoomEventSerializer(
                DefaultEventContentSerializerMappings.room,
                LoggerFactory.default
            ), input
        )
        assertEquals(
            RoomEvent(
                UnknownMessageEventContent("m.dino", "hello"),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
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
                content = NameEventContent("test"),
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
            "content":{
                "name":"test"
            },
            "event_id":"$143273582443PhrSn:server",
            "sender":"@sender:server",
            "room_id":"!room:server",
            "origin_server_ts":1234,
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
            "room_id":"!room:server",
            "origin_server_ts":1234,
            "unsigned":{},
            "state_key":"@user:server",
            "type":"m.room.member"
        }]
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result =
            json.encodeToString(
                ListSerializer(
                    StateEventSerializer(
                        DefaultEventContentSerializerMappings.state,
                        LoggerFactory.default
                    )
                ),
                content
            )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeRedactsEvent() {
        val content = RoomEvent(
            RedactionEventContent("spam", EventId("123", "example.org")),
            EventId("143273582443PhrSn", "example.org"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedData(1234)
        )
        val expectedResult = """
        {
            "content":{
                "reason":"spam"
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{
                "age":1234
            },
            "type":"m.room.redaction",
            "redacts":"$123:example.org"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            RoomEventSerializer(DefaultEventContentSerializerMappings.room, LoggerFactory.default),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeRedactedRoomEvent() {
        val input = """
        {
            "content":{},
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{
                "age":1234,
                "redacted_because":{
                    "content":{
                        "reason":"spam"
                    },
                    "event_id":"$143273582443PhrSn:example.org",
                    "sender":"@example:example.org",
                    "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                    "origin_server_ts":1432735824653,
                    "unsigned":{
                        "age":1234
                    },
                    "type":"m.room.redaction",
                    "redacts":"$143273582443PhrSn:example.org"
                    }
                },
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString(
            RoomEventSerializer(
                DefaultEventContentSerializerMappings.room,
                LoggerFactory.default
            ), input
        )
        assertEquals(
            RoomEvent(
                RedactedRoomEventContent("m.room.message"),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedData(1234)
            ), result
        )
    }

    @Test
    @ExperimentalSerializationApi
    fun shouldSerializeMalformedEvent() {
        val input = """
        {
            "type":"m.room.canonical_alias",
            "content":{
                "alias":"dino",
                "unicorns":[]
            },
            "event_id":"$143273582443PhrSn:example.org",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234,"redactedBecause":null,"transaction_id":null},
            "state_key":"",
            "prev_content":null
        }
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(StateEvent::class.simpleName, result::class.simpleName)
        require(result is StateEvent<*>)
        assertEquals(UnknownStateEventContent::class.simpleName, result.content::class.simpleName)
    }
}