package net.folivo.trixnity.core.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.serializersModuleOf
import net.folivo.trixnity.core.model.MatrixId.RoomAliasId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.UnknownEvent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.*
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
                RoomAliasId("somewhere", "example.org"), result.content.alias
            )
            assertEquals(1234, result.unsigned.age)
            assertEquals("m.room.canonical_alias", result.type)
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
            assertEquals(UserId("example", "example.org"), result.content.creator)
            assertEquals(1234, result.unsigned.age)
            assertEquals("m.room.create", result.type)
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
            assertEquals("unknownEventType", result.type)
            assertEquals("unicorn", result.content["something"]?.jsonPrimitive?.content)
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
        if (result is MessageEvent && resultContent is TextMessageEventContent) {
            assertEquals("org.matrix.custom.html", resultContent.format)
            assertEquals("m.room.message", result.type)
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
        if (result is MessageEvent && resultContent is UnknownMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
            assertEquals("m.unknown", resultContent.messageType)
            assertEquals("m.room.message", result.type)
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
        if (result is MessageEvent && resultContent is UnknownMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
            assertEquals("dino", resultContent.messageType)
            assertEquals("m.room.message", result.type)
        } else {
            fail("resultContent should be of type ${UnknownMessageEventContent::class}")
        }
    }

    @Test
    fun shouldCreateValidMatrixJsonFromMessageEventContent() {
        val result = Json.encodeToString(NoticeMessageEventContent("test"))
        assertEquals("""{"body":"test"}""", result)
    }

    @Serializable
    data class CustomResponse(
        @SerialName("event") val event: RoomEvent<@Contextual Any>
    )

    @Test
    fun shouldDeserializeSubtype() {
        val content = """
        {   
            "event":{
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
        }
    """.trimIndent()
        val result = Json {
            ignoreUnknownKeys = true
            serializersModule = serializersModuleOf(EventSerializer())
        }.decodeFromString<CustomResponse>(content)
        val resultContent = result.event.content
        if (result.event as Event<*> is MessageEvent && resultContent is UnknownMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
            assertEquals("dino", resultContent.messageType)
            assertEquals("m.room.message", result.event.type)
        } else {
            fail("resultContent should be of type ${UnknownMessageEventContent::class}")
        }
    }
}