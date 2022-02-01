package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.UnknownRoomMessageEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializerTest {

    private val json = createMatrixJson()

    @Test
    fun shouldSerializeStateEvent() {
        val content = StateEvent(
            CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedStateEventData(1234),
            ""
        )
        val expectedResult = """
        {
            "content":{
                "alias":"#somewhere:example.org"
            },
            "event_id":"$143273582443PhrSn",
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
            "event_id":"$143273582443PhrSn",
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
            ), input
        )
        assertEquals(
            StateEvent(
                CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedStateEventData(1234),
                ""
            ), result
        )
    }

    @Test
    fun shouldSerializeMessageEvent() {
        val content = MessageEvent(
            RoomMessageEventContent.TextMessageEventContent(
                "hello",
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(1234),
        )
        val expectedResult = """
        {
            "content":{
                "body":"hello",
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.reference"
                },
                "msgtype":"m.text"
            },
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234},
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            MessageEventSerializer(DefaultEventContentSerializerMappings.message),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeMessageEvent() {
        val input = """
        {
            "content":{
                "msgtype":"m.dino",
                "body":"hello",
                "something":"unicorn",
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.reference"
                }
            },
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234},
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ), input
        )
        assertEquals(
            MessageEvent(
                UnknownRoomMessageEventContent(
                    "m.dino", "hello", JsonObject(
                        mapOf(
                            "msgtype" to JsonPrimitive("m.dino"),
                            "body" to JsonPrimitive("hello"),
                            "something" to JsonPrimitive("unicorn"),
                            "m.relates_to" to JsonObject(
                                mapOf(
                                    "event_id" to JsonPrimitive("$1234"),
                                    "rel_type" to JsonPrimitive("m.reference")
                                )
                            )
                        )
                    ), relatesTo = RelatesTo.Reference(EventId("$1234"))
                ),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(1234)
            ), result
        )
    }

    @Test
    fun shouldDeserializeUnknownMessageEvent() {
        val input = """
        {
            "content":{
                "msgtype":"m.dino",
                "body":"hello",
                "something":"unicorn",
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.reference"
                }
            },
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{"age":1234},
            "type":"m.dino"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ), input
        )
        assertEquals(
            MessageEvent(
                UnknownMessageEventContent(
                    JsonObject(
                        mapOf(
                            "msgtype" to JsonPrimitive("m.dino"),
                            "body" to JsonPrimitive("hello"),
                            "something" to JsonPrimitive("unicorn"),
                            "m.relates_to" to JsonObject(
                                mapOf(
                                    "event_id" to JsonPrimitive("$1234"),
                                    "rel_type" to JsonPrimitive("m.reference")
                                )
                            )
                        )
                    ), "m.dino"
                ),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(1234)
            ), result
        )
    }

    @Test
    fun shouldSerializeEventList() {
        val content = listOf(
            StateEvent(
                id = EventId("$143273582443PhrSn"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedStateEventData(),
                originTimestamp = 1234,
                sender = UserId("sender", "server"),
                content = NameEventContent("test"),
                stateKey = ""
            ),
            StateEvent(
                id = EventId("$143273584443PhrSn"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedStateEventData(),
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
            "event_id":"$143273582443PhrSn",
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
            "event_id":"$143273584443PhrSn",
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
                    )
                ),
                content
            )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeRedactsEvent() {
        val content = MessageEvent(
            RedactionEventContent("spam", EventId("$123")),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(1234)
        )
        val expectedResult = """
        {
            "content":{
                "reason":"spam"
            },
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{
                "age":1234
            },
            "type":"m.room.redaction",
            "redacts":"$123"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            MessageEventSerializer(DefaultEventContentSerializerMappings.message),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeRedactedMessageEvent() {
        val input = """
        {
            "content":{},
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "unsigned":{
                "age":1234,
                "redacted_because":{
                    "content":{
                        "reason":"spam"
                    },
                    "event_id":"$143273582443PhrSn",
                    "sender":"@example:example.org",
                    "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                    "origin_server_ts":1432735824653,
                    "unsigned":{
                        "age":1234
                    },
                    "type":"m.room.redaction",
                    "redacts":"$143273582443PhrSn"
                    }
                },
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ), input
        )
        assertEquals(
            MessageEvent(
                RedactedMessageEventContent("m.room.message"),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(
                    1234, redactedBecause = MessageEvent(
                        RedactionEventContent("spam", EventId("$143273582443PhrSn")),
                        EventId("$143273582443PhrSn"),
                        UserId("example", "example.org"),
                        RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                        1432735824653,
                        UnsignedMessageEventData(1234)
                    )
                )
            ), result
        )
    }

    @Test
    fun shouldSerializeRedactedMessageEvent() {
        val content = MessageEvent(
            RedactedMessageEventContent("m.room.message"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(
                1234, redactedBecause = MessageEvent(
                    RedactionEventContent("spam", EventId("$143273582443PhrSn")),
                    EventId("$143273582443PhrSn"),
                    UserId("example", "example.org"),
                    RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                    1432735824653,
                    UnsignedMessageEventData(1234)
                )
            )
        )
        val expectedResult = """
        {
            "content":{},
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{
                "age":1234,
                "redacted_because":{
                    "content":{
                        "reason":"spam"
                    },
                    "event_id":"$143273582443PhrSn",
                    "sender":"@example:example.org",
                    "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                    "origin_server_ts":1432735824653,
                    "unsigned":{
                        "age":1234
                    },
                    "type":"m.room.redaction",
                    "redacts":"$143273582443PhrSn"
                    }
                },
            "type":"m.room.message"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(
            MessageEventSerializer(DefaultEventContentSerializerMappings.message),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    @ExperimentalSerializationApi
    fun shouldDeserializeUnknownAccountDataEvent() {
        val input = """
            {
                "type": "org.example.mynamespace.custom",
                "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                "content": {
                    "ancestor_of_chicken": "dinos"
                }
            }
            """.trimIndent()
        val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(
            RoomAccountDataEvent(
                UnknownRoomAccountDataEventContent(
                    JsonObject(
                        mapOf(
                            "ancestor_of_chicken" to JsonPrimitive("dinos")
                        )
                    ),
                    "org.example.mynamespace.custom"
                ),
                RoomId("!jEsUZKDJdhlrceRyVU:example.org"),
            ), result
        )
    }

    @ExperimentalSerializationApi
    @Test
    fun shouldSerializeUnknownAccountDataEvent() {
        val event = RoomAccountDataEvent(
            UnknownRoomAccountDataEventContent(
                JsonObject(
                    mapOf(
                        "ancestor_of_chicken" to JsonPrimitive("dinos")
                    )
                ),
                "org.example.mynamespace.custom"
            ),
            RoomId("!jEsUZKDJdhlrceRyVU:example.org"),
        )
        val expected = """
            {
                "content":{
                    "ancestor_of_chicken":"dinos"
                },
                "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                "type":"org.example.mynamespace.custom"
            }
            """.trimIndent().lines().joinToString("") { it.trim() }
        val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, event)
        assertEquals(expected, result)
    }

    @Test
    @ExperimentalSerializationApi
    fun shouldDeserializeMalformedEvent() {
        val input = """
        {
            "type":"m.room.canonical_alias",
            "content":{
                "alias":"dino",
                "unicorns":[]
            },
            "event_id":"$143273582443PhrSn",
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
        assertEquals(
            StateEvent(
                UnknownStateEventContent(
                    JsonObject(
                        mapOf(
                            "alias" to JsonPrimitive("dino"),
                            "unicorns" to JsonArray(listOf())
                        )
                    ), "m.room.canonical_alias"
                ),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedStateEventData(1234),
                ""
            ), result
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun shouldSerializeMalformedEvent() {
        val content = StateEvent(
            UnknownStateEventContent(
                JsonObject(
                    mapOf(
                        "alias" to JsonPrimitive("dino"),
                        "unicorns" to JsonArray(listOf())
                    )
                ), "m.room.canonical_alias"
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedStateEventData(1234),
            ""
        )
        val expectedResult = """
        {
            "content":{
                "alias":"dino",
                "unicorns":[]
            },
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234},
            "state_key":"",
            "type":"m.room.canonical_alias"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, content)
        assertEquals(expectedResult, result)
    }
}