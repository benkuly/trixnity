package net.folivo.trixnity.core.serialization.events

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.RedactedEventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedMessageEventData
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.*
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.Unknown
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.trimToFlatJson
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientEventSerializerTest {

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
            RoomMessageEventContent.TextBased.Text(
                "hello",
                relatesTo = RelatesTo.Reference(EventId("$1234"))
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(
                1234, relations =
                mapOf(
                    RelationType.Unknown("org.example.possible_annotations") to
                            ServerAggregation.Unknown(
                                RelationType.Unknown("org.example.possible_annotations"), buildJsonArray {
                                    add(buildJsonObject {
                                        put("key", JsonPrimitive("👍"))
                                        put("count", JsonPrimitive(3))
                                    })
                                })
                )
            ),
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
            "unsigned":{
                "age":1234,
                "m.relations": {
                  "org.example.possible_annotations": [
                    {
                      "count": 3,
                      "key": "👍"
                    }
                  ]
                }
            }
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeMessageEventWithRelatesToAndCopyNewContent() {
        val content = MessageEvent(
            RoomMessageEventContent.TextBased.Text(
                "hello",
                relatesTo = RelatesTo.Replace(
                    EventId("$1234"),
                    newContent = RoomMessageEventContent.TextBased.Text("hello-new")
                )
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(),
        )
        val expectedResult = """
        {
            "content":{
                "body":"hello",
                "m.new_content":{
                    "body":"hello-new",
                    "msgtype":"m.text"
                },
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.replace"
                },
                "msgtype":"m.text"
            },
            "event_id":"$143273582443PhrSn",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.message",
            "unsigned":{}
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeMessageEventWithRelatesToAndDontCopyNewContentWhenEncrypted() {
        val content = MessageEvent(
            MegolmEncryptedMessageEventContent(
                "ciphercipher",
                sessionId = "sessionId",
                relatesTo = RelatesTo.Replace(
                    EventId("$1234"),
                    newContent = RoomMessageEventContent.TextBased.Text("hello-new")
                )
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(),
        )
        val expectedResult = """
        {
            "content":{
                "algorithm":"m.megolm.v1.aes-sha2",
                "ciphertext":"ciphercipher",
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.replace"
                },
                "session_id":"sessionId"
            },
            "event_id":"$143273582443PhrSn",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.encrypted",
            "unsigned":{}
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeMessageEventWithExternalUrl() {
        val content = MessageEvent(
            RoomMessageEventContent.TextBased.Text(
                "hello",
                externalUrl = "http://some-external-url.test"
            ),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(),
        )
        val expectedResult = """
        {
            "content":{
                "body":"hello",
                "external_url": "http://some-external-url.test",
                "msgtype":"m.text"
            },
            "event_id":"$143273582443PhrSn",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.message",
            "unsigned":{}
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeMessageEvent() {
        val input = """
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
            "unsigned":{
                "age":1234,
                "m.relations": {
                  "org.example.possible_annotations": [
                    {
                      "key": "👍",
                      "count": 3
                    }
                  ]
                }
            }
        }
    """.trimIndent()

        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            input
        )
        assertEquals(
            MessageEvent(
                RoomMessageEventContent.TextBased.Text(
                    "hello",
                    relatesTo = RelatesTo.Reference(EventId("$1234"))
                ),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(
                    1234, relations =
                    mapOf(
                        RelationType.Unknown("org.example.possible_annotations") to
                                ServerAggregation.Unknown(
                                    RelationType.Unknown("org.example.possible_annotations"), buildJsonArray {
                                        add(buildJsonObject {
                                            put("key", JsonPrimitive("👍"))
                                            put("count", JsonPrimitive(3))
                                        })
                                    })
                    )
                ),
            ), result
        )
    }

    @Test
    fun shouldDeserializeMessageEventWithRelatesToAndCopyNewContent() {
        val input = """
        {
            "content":{
                "body":"hello",
                "m.new_content":{
                    "body":"hello-new",
                    "msgtype":"m.text"
                },
                "m.relates_to":{
                    "event_id":"$1234",
                    "rel_type":"m.replace"
                },
                "msgtype":"m.text"
            },
            "event_id":"$143273582443PhrSn",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "sender":"@example:example.org",
            "type":"m.room.message",
            "unsigned":{}
        }
    """.trimIndent()

        val result = json.decodeFromString(
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            input
        )
        assertEquals(
            MessageEvent(
                RoomMessageEventContent.TextBased.Text(
                    "hello",
                    relatesTo = RelatesTo.Replace(
                        EventId("$1234"),
                        newContent = RoomMessageEventContent.TextBased.Text("hello-new")
                    )
                ),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(),
            ), result
        )
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
                    "rel_type":"something"
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
                RoomMessageEventContent.TextBased.Text(
                    body = "hello", relatesTo = RelatesTo.Unknown(buildJsonObject {
                        put("event_id", JsonPrimitive(24))
                        put("rel_type", JsonPrimitive("something"))
                    }, EventId("24"), RelationType.Unknown("something"), null)
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
    fun shouldDeserializeMessageEventWithExternalUrl() {
        val input = """
        {
            "content":{
                "msgtype":"m.text",
                "body":"hello",
                "external_url": "http://some-external-url.test"
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
                RoomMessageEventContent.TextBased.Text(
                    body = "hello",
                    externalUrl = "http://some-external-url.test"
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
                Unknown(
                    "m.dino", "hello", null, null, JsonObject(
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
                UnknownEventContent(
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
            RedactionEventContent(EventId("$123"), "spam"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(1234)
        )
        val expectedResult = """
        {
            "content":{
                "reason":"spam",
                "redacts":"$123"
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
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
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
                RedactedEventContent("m.room.message"),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                UnsignedMessageEventData(
                    1234, redactedBecause = MessageEvent(
                        RedactionEventContent(EventId("$143273582443PhrSn"), "spam"),
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
    fun shouldDeserializeStateEventWithEmptyContent() {
        val input = """
        {
            "content":{},
            "event_id":"$143273582443PhrSn",
            "sender":"@example:example.org",
            "origin_server_ts":1432735824653,
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "type":"m.room.avatar",
            "state_key":""
        }
    """.trimToFlatJson()
        val result = json.decodeFromString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
            ), input
        )
        assertEquals(
            StateEvent(
                AvatarEventContent(),
                EventId("$143273582443PhrSn"),
                UserId("example", "example.org"),
                RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                1432735824653,
                stateKey = "",
            ), result
        )
    }

    @Test
    fun shouldSerializeRedactedMessageEvent() {
        val content = MessageEvent(
            RedactedEventContent("m.room.message"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedMessageEventData(
                1234, redactedBecause = MessageEvent(
                    RedactionEventContent(EventId("$143273582443PhrSn"), "spam"),
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
                        "reason":"spam",
                        "redacts":"$143273582443PhrSn"
                    },
                    "event_id":"$143273582443PhrSn",
                    "origin_server_ts":1432735824653,
                    "redacts":"$143273582443PhrSn",
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
            MessageEventSerializer(
                DefaultEventContentSerializerMappings.message,
            ),
            content
        )
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeRedactedUnsignedDataInStateEvent() {
        val input = """
        {
            "content": {
                "name": "name"
            },
            "event_id": "${'$'}143273582443PhrSn",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.name",
            "unsigned": {
                "prev_content": {}
            }
        }
    """.trimToFlatJson()
        val result = json.decodeFromString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
            ),
            input
        )
        result shouldBe StateEvent(
            NameEventContent("name"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedStateEventData(
                previousContent = RedactedEventContent("m.room.name")
            ),
            "",
        )
    }

    @Test
    fun shouldSerializeRedactedUnsignedDataInStateEvent() {
        val input = StateEvent(
            NameEventContent("name"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedStateEventData(
                previousContent = RedactedEventContent("m.room.name")
            ),
            "",
        )
        val output = """
        {
            "content": {
                "name": "name"
            },
            "event_id": "${'$'}143273582443PhrSn",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.name",
            "unsigned": {
                "prev_content": {}
            }
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
            ),
            input
        )
        result shouldBe output
    }

    @Test
    fun shouldDeserializeRedactedStateEvent() {
        val input = """
        {
            "content": {},
            "event_id": "${'$'}143273582443PhrSn",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.name",
            "unsigned": {
                "prev_content": {
                    "name": "prev"
                }
            }
        }
    """.trimToFlatJson()
        val result = json.decodeFromString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
            ),
            input
        )
        result shouldBe
                StateEvent(
                    RedactedEventContent("m.room.name"),
                    EventId("$143273582443PhrSn"),
                    UserId("example", "example.org"),
                    RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
                    1432735824653,
                    UnsignedStateEventData(
                        previousContent = NameEventContent("prev")
                    ),
                    "",
                )
    }

    @Test
    fun shouldSerializeRedactedStateEvent() {
        val input = StateEvent(
            RedactedEventContent("m.room.name"),
            EventId("$143273582443PhrSn"),
            UserId("example", "example.org"),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
            1432735824653,
            UnsignedStateEventData(
                previousContent = NameEventContent("prev")
            ),
            "",
        )
        val output = """
        {
            "content": {},
            "event_id": "${'$'}143273582443PhrSn",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.name",
            "unsigned": {
                "prev_content": {
                    "name": "prev"
                }
            }
        }
    """.trimToFlatJson()
        val result = json.encodeToString(
            StateEventSerializer(
                DefaultEventContentSerializerMappings.state,
            ),
            input
        )
        result shouldBe output
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
                UnknownEventContent(
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
            UnknownEventContent(
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
        val serializer = json.serializersModule.getContextual(RoomEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(
            StateEvent(
                UnknownEventContent(
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
            UnknownEventContent(
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
        val serializer = json.serializersModule.getContextual(RoomEvent::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, content)
        assertEquals(expectedResult, result)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun shouldSerializeEncryptedEventWithRelatesTo() {
        val input = MessageEvent(
            MegolmEncryptedMessageEventContent(
                ciphertext = "jdlskjfldjsvjJIODJKLfjdlfkjdfioj/sdfjijfDSHDUH",
                senderKey = Key.Curve25519Key(null, "YWO+ZYV1tFTAFPu3A3609oHUF4VYRPDMjizgV48O2jg"),
                deviceId = "GNAHNGTKNL",
                sessionId = "8798dSJJ878789dfjJKDSF",
                relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("$7sxeT7hzXMlQ7cF2xKJAThT0h4jUvy0-RRgsmF7IZEY")))
            ),
            id = EventId("\$dGD9Qv39oKujC6MIbJUWSVrecLzdh0I1i00o2j6r24A"),
            roomId = RoomId("!aNgXnqwYApWloKSPKD:imbitbu.de"),
            sender = UserId("@user:localhost"),
            originTimestamp = 1643815115835,
            unsigned = UnsignedMessageEventData(age = 241)
        )
        val serializer = json.serializersModule.getContextual(RoomEvent::class)
        requireNotNull(serializer)
        val result = json.encodeToString(serializer, input)
        assertEquals(
            """
        {
            "content": {
             "algorithm": "m.megolm.v1.aes-sha2",
             "ciphertext": "jdlskjfldjsvjJIODJKLfjdlfkjdfioj/sdfjijfDSHDUH",
             "device_id": "GNAHNGTKNL",
             "m.relates_to": {
               "m.in_reply_to": {
                 "event_id": "${'$'}7sxeT7hzXMlQ7cF2xKJAThT0h4jUvy0-RRgsmF7IZEY"
               }
             },
             "sender_key": "YWO+ZYV1tFTAFPu3A3609oHUF4VYRPDMjizgV48O2jg",
             "session_id": "8798dSJJ878789dfjJKDSF"
           },
           "event_id": "${'$'}dGD9Qv39oKujC6MIbJUWSVrecLzdh0I1i00o2j6r24A",
           "origin_server_ts": 1643815115835,
           "room_id": "!aNgXnqwYApWloKSPKD:imbitbu.de",
           "sender": "@user:localhost",
           "type": "m.room.encrypted",
           "unsigned": {
             "age": 241
           }
        }
        """.trimToFlatJson(), result
        )
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
        val serializer = json.serializersModule.getContextual(RoomEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, input)
        assertEquals(
            MessageEvent(
                MegolmEncryptedMessageEventContent(
                    ciphertext = "jdlskjfldjsvjJIODJKLfjdlfkjdfioj/sdfjijfDSHDUH",
                    senderKey = Key.Curve25519Key(null, "YWO+ZYV1tFTAFPu3A3609oHUF4VYRPDMjizgV48O2jg"),
                    deviceId = "GNAHNGTKNL",
                    sessionId = "8798dSJJ878789dfjJKDSF",
                    relatesTo = RelatesTo.Reply(RelatesTo.ReplyTo(EventId("$7sxeT7hzXMlQ7cF2xKJAThT0h4jUvy0-RRgsmF7IZEY")))
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
                        EventId("\$wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s") to mapOf(
                            ReceiptType.Read to mapOf(
                                UserId("user1", "localhost") to Receipt(1644259179796L),
                                UserId("user2", "localhost") to Receipt(1644258600722L),
                            )
                        ),
                        EventId("\$zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE") to mapOf(
                            ReceiptType.Read to mapOf(
                                UserId("user3", "localhost") to Receipt(1644267366690L)
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
                    EventId("\$wUeWup1q4tsPBG-zHFicJTHpY30cmxjgV-LW0ZAOB9s") to mapOf(
                        ReceiptType.Read to mapOf(
                            UserId("user1", "localhost") to Receipt(1644259179796L),
                            UserId("user2", "localhost") to Receipt(1644258600722L),
                        )
                    ),
                    EventId("\$zu9ULQ-V3AGcshRNfByIb3sVZ62cTUpeZcdIJ3fBNXE") to mapOf(
                        ReceiptType.Read to mapOf(
                            UserId("user3", "localhost") to Receipt(1644267366690L)
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