package net.folivo.trixnity.core.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.MatrixId.RoomAliasId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.UnknownEvent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.UnknownMessageEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EventSerializerTest {

    @Test
    fun shouldCreateSubtypeFromStateEvent() {
        val content = """
        {
            "content": {
                "alias": "#somewhere:example.org"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.canonical_alias",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json.decodeFromString(EventSerializer(), content)
        if (result is CanonicalAliasEvent) {
            assertEquals(
                result.content.alias,
                RoomAliasId("somewhere", "example.org")
            )
            assertEquals(result.unsigned.age, 1234)
            assertEquals(result.type, "m.room.canonical_alias")
        } else {
            fail("result should be of type ${CanonicalAliasEvent::class} but was ${result::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromRoomEvent() {
        val eventId = "\$something:example.org"
        val content = """
        {
            "content": {
                "creator": "@example:example.org",
                "m.federate": true,
                "predecessor": {
                    "event_id": "$eventId",
                    "room_id": "!oldroom:example.org"
                },
                "room_version": "1"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "state_key": "",
            "type": "m.room.create",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json.decodeFromString(EventSerializer(), content)
        if (result is CreateEvent) {
            assertEquals(result.content.creator, UserId("example", "example.org"))
            assertEquals(result.unsigned.age, 1234)
            assertEquals(result.type, "m.room.create")
        } else {
            fail("result should be of type ${MessageEvent::class} but was ${result::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromEventEvenIfItsTypeIsUnknown() {
        val content = """
        {
            "content": {
                "something": "unicorn"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "unknownEventType",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json { ignoreUnknownKeys = true }.decodeFromString(EventSerializer(), content)
        if (result is UnknownEvent) {
            assertEquals(result.type, "unknownEventType")
            assertEquals(result.content["something"]?.jsonPrimitive?.content, "unicorn")
        } else {
            fail("result should be of type ${UnknownEvent::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromMessageEvent() {
        val content = """
        {
            "content": {
                "body": "This is an example text message",
                "format": "org.matrix.custom.html",
                "formatted_body": "<b>This is an example text message</b>",
                "msgtype": "m.text"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "m.room.message",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json.decodeFromString(EventSerializer(), content)
        val resultContent = result.content
        if (result is MessageEvent<*> && resultContent is TextMessageEventContent) {
            assertEquals(resultContent.format, "org.matrix.custom.html")
            assertEquals(result.type, "m.room.message")
        } else {
            fail("resultContent should be of type ${TextMessageEventContent::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromMessageEventEvenIfItsTypeIsUnknown() {
        val content = """
        {
            "content": {
                "body": "This is an example text message",
                "format": "org.matrix.custom.html",
                "formatted_body": "<b>This is an example text message</b>",
                "msgtype": "m.unknown"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "m.room.message",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json { ignoreUnknownKeys = true }.decodeFromString(EventSerializer(), content)
        val resultContent = result.content
        if (result is MessageEvent<*> && resultContent is UnknownMessageEventContent) {
            assertEquals(resultContent.body, "This is an example text message")
            assertEquals(resultContent.messageType, "m.unknown")
            assertEquals(result.type, "m.room.message")
        } else {
            fail("resultContent should be of type ${UnknownMessageEventContent::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromMessageEventEvenIfItsTypeIsNull() {
        val content = """
        {
            "content": {
                "body": "This is an example text message",
                "format": "org.matrix.custom.html",
                "formatted_body": "<b>This is an example text message</b>",
                "msgtype": "dino"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "m.room.message",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val result = Json { ignoreUnknownKeys = true }.decodeFromString(EventSerializer(), content)
        val resultContent = result.content
        if (result is MessageEvent<*> && resultContent is UnknownMessageEventContent) {
            assertEquals(resultContent.body, "This is an example text message")
            assertEquals(resultContent.messageType, "dino")
            assertEquals(result.type, "m.room.message")
        } else {
            fail("resultContent should be of type ${UnknownMessageEventContent::class}")
        }
    }
//
//    @Test
//    fun `should create valid matrix json from message event content`() {
//        val result = jsonContent.write(TextMessageEventContent("test"))
//        assertThat(result).extractingJsonPathStringValue("@.msgtype").isEqualTo("m.text")
//        assertThat(result).extractingJsonPathStringValue("@.body").isEqualTo("test")
//        println(result.json)
//    }
}