package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt.ReadReceipt
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.UnknownRoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.trimToFlatJson
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSerializerTest {

    private val json = createMatrixEventJson()

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
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "state_key":"",
            "type":"m.room.canonical_alias",
            "unsigned":{"age":1234}
        }
    """.trimToFlatJson()
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
    """.trimToFlatJson()
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
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.message",
            "unsigned":{"age":1234}
            
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            MessageEventSerializer(DefaultEventContentSerializerMappings.message),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeMessageEventWithMalformedRelatesTo() {
        val input = """
        {
            "content":{
                "msgtype":"m.text",
                "body":"hello",
                "m.relates_to":{
                    "event_id":24,
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
    """.trimToFlatJson()
        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ), input
        )
        assertEquals(
            MessageEvent(
                RoomMessageEventContent.TextMessageEventContent(
                    body = "hello", relatesTo = RelatesTo.Unknown(buildJsonObject {
                        put("event_id", JsonPrimitive(24))
                        put("rel_type", JsonPrimitive("m.reference"))
                    })
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
    fun shouldDeserializeMalformedUnknownMessageEvent() {
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
    """.trimToFlatJson()
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
    """.trimToFlatJson()
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
                content = MemberEventContent(membership = Membership.INVITE)
            )
        )
        val expectedResult = """
        [{
            "content":{
                "name":"test"
            },
            "event_id":"$143273582443PhrSn",
            "origin_server_ts":1234,
            "room_id":"!room:server",
            "sender":"@sender:server",
            "state_key":"",
            "type":"m.room.name",
            "unsigned":{}
        },
        {
            "content":{
                "membership":"invite"
            },
            "event_id":"$143273584443PhrSn",
            "origin_server_ts":1234,
            "room_id":"!room:server",
            "sender":"@sender:server",
            "state_key":"@user:server",
            "type":"m.room.member",
            "unsigned":{}
        }]
    """.trimToFlatJson()
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
            "origin_server_ts":1432735824653,
             "redacts":"$123",
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.redaction",
            "unsigned":{
                "age":1234
            }
        }
    """.trimToFlatJson()
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
    """.trimToFlatJson()
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
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.message",
            "unsigned":{
                "age":1234,
                "redacted_because":{
                    "content":{
                        "reason":"spam"
                    },
                    "event_id":"$143273582443PhrSn",
                    "origin_server_ts":1432735824653,
                    "redacts":"${'$'}143273582443PhrSn",
                    "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                    "sender":"@example:example.org",
                    "type":"m.room.redaction",
                    "unsigned":{
                        "age":1234
                    }
                    }
                }
        }
    """.trimToFlatJson()
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
            """.trimToFlatJson()
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
            "type":"m.room.member",
            "content":{
                "membership":"dino",
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
                            "membership" to JsonPrimitive("dino"),
                            "unicorns" to JsonArray(listOf())
                        )
                    ), "m.room.member"
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
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "state_key":"",
            "type":"m.room.canonical_alias",
            "unsigned":{"age":1234}
        }
    """.trimToFlatJson()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, content)
        assertEquals(expectedResult, result)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun shouldDeserializeEncryptedEventWithRelatesTo() {
        val input = """
        {
           "type": "m.room.encrypted",
           "sender": "@user:localhost",
           "content": {
             "algorithm": "m.megolm.v1.aes-sha2",
             "sender_key": "YWO+ZYV1tFTAFPu3A3609oHUF4VYRPDMjizgV48O2jg",
             "ciphertext": "jdlskjfldjsvjJIODJKLfjdlfkjdfioj/sdfjijfDSHDUH",
             "session_id": "8798dSJJ878789dfjJKDSF",
             "device_id": "GNAHNGTKNL",
             "m.relates_to": {
               "m.in_reply_to": {
                 "event_id": "${'$'}7sxeT7hzXMlQ7cF2xKJAThT0h4jUvy0-RRgsmF7IZEY"
               }
             }
           },
           "origin_server_ts": 1643815115835,
           "unsigned": {
             "age": 241
           },
           "event_id": "${'$'}dGD9Qv39oKujC6MIbJUWSVrecLzdh0I1i00o2j6r24A",
           "room_id": "!aNgXnqwYApWloKSPKD:imbitbu.de"
        }
        """.trimIndent()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(
            MessageEvent(
                EncryptedEventContent.MegolmEncryptedEventContent(
                    ciphertext = "jdlskjfldjsvjJIODJKLfjdlfkjdfioj/sdfjijfDSHDUH",
                    senderKey = Key.Curve25519Key(null, "YWO+ZYV1tFTAFPu3A3609oHUF4VYRPDMjizgV48O2jg"),
                    deviceId = "GNAHNGTKNL",
                    sessionId = "8798dSJJ878789dfjJKDSF",
                    relatesTo = RelatesTo.Unknown(
                        raw = JsonObject(
                            mapOf(
                                "m.in_reply_to" to JsonObject(
                                    mapOf(
                                        "event_id" to JsonPrimitive(
                                            "$7sxeT7hzXMlQ7cF2xKJAThT0h4jUvy0-RRgsmF7IZEY"
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                id = EventId("\$dGD9Qv39oKujC6MIbJUWSVrecLzdh0I1i00o2j6r24A"),
                roomId = RoomId("!aNgXnqwYApWloKSPKD:imbitbu.de"),
                sender = UserId("@user:localhost"),
                originTimestamp = 1643815115835,
                unsigned = UnsignedMessageEventData(age = 241)
            ), result
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun shouldDeserializeReceipt() {
        val input = """
        {
            "type": "m.receipt",
            "content": {
                "${'$'}wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s": {
                    "m.read": {
                        "@user1:localhost": {
                            "ts":1644259179796,"hidden":false
                        },
                        "@user2:localhost": {
                            "ts":1644258600722,"hidden":false
                        }
                   }
                },
                "${'$'}zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE": {
                    "m.read": {
                        "@user3:localhost": {
                            "ts":1644267366690,"hidden":false
                        }
                    }
                }
            }
        }
        """.trimIndent()
        val serializer = json.serializersModule.getContextual(EphemeralEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(
            EphemeralEvent(
                ReceiptEventContent(
                    events = mapOf(
                        EventId("\$wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s") to setOf(
                            ReadReceipt(
                                read = mapOf(
                                    UserId("user1", "localhost") to ReadReceipt.ReadEvent(1644259179796L),
                                    UserId("user2", "localhost") to ReadReceipt.ReadEvent(1644258600722L),
                                )
                            )
                        ),
                        EventId("\$zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE") to setOf(
                            ReadReceipt(
                                read = mapOf(
                                    UserId("user3", "localhost") to ReadReceipt.ReadEvent(1644267366690L)
                                )
                            )
                        )
                    ),
                ),
            ), result
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun shouldSerializeReceipt() {
        val receipt = EphemeralEvent(
            ReceiptEventContent(
                events = mapOf(
                    EventId("\$wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s") to setOf(
                        ReadReceipt(
                            read = mapOf(
                                UserId("user1", "localhost") to ReadReceipt.ReadEvent(1644259179796L),
                                UserId("user2", "localhost") to ReadReceipt.ReadEvent(1644258600722L),
                            )
                        )
                    ),
                    EventId("\$zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE") to setOf(
                        ReadReceipt(
                            read = mapOf(
                                UserId("user3", "localhost") to ReadReceipt.ReadEvent(1644267366690L)
                            )
                        )
                    )
                ),
            ),
        )
        val expectedResult = """
        {
            "content":{
                "${'$'}wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s":{
                    "m.read":{
                        "@user1:localhost":{
                            "ts":1644259179796
                        },
                        "@user2:localhost":{
                            "ts":1644258600722
                        }
                   }
                },
                "${'$'}zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE":{
                    "m.read":{
                        "@user3:localhost":{
                            "ts":1644267366690
                        }
                    }
                }
            },
            "type":"m.receipt"
        }
        """.trimToFlatJson()
        val serializer = json.serializersModule.getContextual(EphemeralEvent::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, receipt)
        assertEquals(expectedResult, result)
    }
}