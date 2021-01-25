package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event.RoomEvent
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedData
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.DefaultMessageEventContent
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializerTest {

    private val roomEventSerializer = RoomEventSerializer(DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS)
    private val stateEventSerializer = StateEventSerializer(DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS)
    private val strippedStateEventSerializer = StrippedStateEventSerializer(DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS)
    private val eventSerializer = EventSerializer(
        roomEventSerializer,
        stateEventSerializer,
        strippedStateEventSerializer,
        DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS,
        DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
    )
    private val json = Json {
        classDiscriminator = "ignore"
        ignoreUnknownKeys = true
        serializersModule = createEventSerializersModule(
            DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS,
            DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
        )
    }


    @Test
    fun shouldSerializeStateEvent() {
        val content = StateEvent(
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
        val result = json.encodeToString(eventSerializer, content)
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
        val result = json.decodeFromString(eventSerializer, input)
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
            DefaultMessageEventContent("m.dino", "hello"),
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
        val result = json.encodeToString(eventSerializer, content)
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
        val result = json.decodeFromString(eventSerializer, input)
        assertEquals(
            RoomEvent(
                DefaultMessageEventContent("m.dino", "hello"),
                EventId("143273582443PhrSn", "example.org"),
                UserId("example", "example.org"),
                1432735824653,
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                UnsignedData(1234)
            ), result
        )
    }
}