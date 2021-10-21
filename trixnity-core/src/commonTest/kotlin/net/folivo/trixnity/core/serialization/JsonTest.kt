package net.folivo.trixnity.core.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.MatrixId.RoomAliasId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.UnknownBasicEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.FullyReadEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.UnknownMessageEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@ExperimentalSerializationApi
class JsonTest {
    private val json = createMatrixJson()

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
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        if (result is MessageEvent<*>) {
            val resultContent = result.content
            if (resultContent is TextMessageEventContent)
                assertEquals("org.matrix.custom.html", resultContent.format)
            else fail("resultContent should be of type ${TextMessageEventContent::class}")
        } else {
            fail("resultContent should be of type ${TextMessageEventContent::class}")
        }
    }

    @Test
    fun shouldHandleUnsignedDateRedactedBecause() {
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
                "age": 1234,
                "redacted_because": {
                    "content": {
                        "reason": "Spamming"
                    },
                    "event_id": "${'$'}143273582443PhrSn:example.org",
                    "origin_server_ts": 1432735824653,
                    "redacts": "${'$'}123:example.org",
                    "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                    "sender": "@example:example.org",
                    "type": "m.room.redaction",
                    "unsigned": {
                        "age": 1234
                    }
                }
            }
        }
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        if (result is MessageEvent<*>) {
            val redactedBecauseEventContent = result.unsigned?.redactedBecause?.content
            if (redactedBecauseEventContent is RedactionEventContent)
                assertEquals("Spamming", redactedBecauseEventContent.reason)
            else fail("resultContent should be of type ${RedactionEventContent::class} but was ${redactedBecauseEventContent?.let { it::class }}")
        } else {
            fail("resultContent should be of type ${MessageEvent::class}")
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
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        if (result is MessageEvent<*>) {
            val resultContent = result.content
            if (resultContent is UnknownMessageEventContent)
                assertEquals("This is an example text message", resultContent.body)
            else fail("resultContent should be of type ${UnknownMessageEventContent::class}")
        } else {
            fail("result should be of type ${MessageEvent::class}")
        }
    }

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
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        val eventContent = result.content
        if (eventContent is CanonicalAliasEventContent) {
            assertEquals(RoomAliasId("somewhere", "example.org"), eventContent.alias)
            assertEquals(1234, result.unsigned?.age)
        } else fail("should be ${CanonicalAliasEventContent::class}")
    }

    @Test
    fun shouldCreateSubtypeFromRoomEvent() {
        val content = """
        {
            "content": {
                "reason": "Spamming"
            },
            "event_id": "$143273582443PhrSn:example.org",
            "origin_server_ts": 1432735824653,
            "redacts": "$123:example.org",
            "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
            "sender": "@example:example.org",
            "type": "m.room.redaction",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(MessageEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        val eventContent = result.content
        if (eventContent is RedactionEventContent) {
            assertEquals("Spamming", eventContent.reason)
            assertEquals(1234, result.unsigned?.age)
            assertEquals("$123:example.org", eventContent.redacts.full)
        } else fail("should be ${RedactionEventContent::class}")
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
            "sender": "@example:example.org",
            "type": "unknownEventType",
            "unsigned": {
                "age": 1234
            }
        }
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        val resultContent = result.content
        if (result is BasicEvent && resultContent is UnknownBasicEventContent) {
            assertEquals("unknownEventType", resultContent.eventType)
            assertEquals("unicorn", resultContent.raw.jsonObject["something"]?.jsonPrimitive?.content)
        } else {
            fail("result should be of type ${BasicEvent::class} but was ${result::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromEventListEvenIfItsTypeIsUnknown() {
        val content = """
            [
                {
                    "content": {
                        "something": "unicorn1"
                    },
                    "event_id": "$143273582443PhrSn:example.org",
                    "origin_server_ts": 1432735824653,
                    "sender": "@example:example.org",
                    "type": "unknownEventType1",
                    "unsigned": {
                        "age": 1234
                    }
                },
                {
                    "content": {
                        "something": "unicorn2"
                    },
                    "event_id": "$1432735811113PhrSn:example.org",
                    "origin_server_ts": 1432735224653,
                    "sender": "@example:example.org",
                    "type": "unknownEventType2",
                    "unsigned": {
                        "age": 1234
                    }
                }
        ]
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(Event::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(ListSerializer(serializer), content)
        val result1 = result[0]
        val result2 = result[1]
        val resultContent1 = result1.content
        val resultContent2 = result2.content
        if (result1 is BasicEvent && result2 is BasicEvent && resultContent1 is UnknownBasicEventContent && resultContent2 is UnknownBasicEventContent) {
            assertEquals("unknownEventType1", resultContent1.eventType)
            assertEquals("unknownEventType2", resultContent2.eventType)
            assertEquals("unicorn1", resultContent1.raw.jsonObject["something"]?.jsonPrimitive?.content)
            assertEquals("unicorn2", resultContent2.raw.jsonObject["something"]?.jsonPrimitive?.content)
        } else {
            fail("result should be of type ${BasicEvent::class}")
        }
    }

    @Test
    fun shouldCreateSubtypeFromEphemeralEvent() {
        val content = """
        {
            "content": {
                "avatar_url": "mxc://localhost:wefuiwegh8742w",
                "currently_active": false,
                "last_active_ago": 2478593,
                "presence": "online",
                "status_msg": "Making cupcakes"
            },
            "sender": "@example:localhost",
            "type": "m.presence"
        }
    """.trimIndent()
        val serializer = json.serializersModule.getContextual(EphemeralEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        val eventContent = result.content
        assertEquals(PresenceEventContent::class, eventContent::class)
    }

    @Test
    fun shouldCreateSubtypeFromFullyReadEvent() {
        val content = """
            {
                "content": {
                    "event_id": "$143273582443PhrSn:example.org"
                },
                "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                "type": "m.fully_read"
            }
            """.trimIndent()
        val serializer = json.serializersModule.getContextual(RoomAccountDataEvent::class)
        requireNotNull(serializer)
        val result = json.decodeFromString(serializer, content)
        val eventContent = result.content
        assertEquals(FullyReadEventContent::class, eventContent::class)
    }

    @Serializable
    data class CustomResponse(
        @Contextual @SerialName("event") val event: MessageEvent<*>
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
        val result = json.decodeFromString<CustomResponse>(content)
        val resultContent = result.event.content
        if (resultContent is UnknownMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
            assertEquals("dino", resultContent.type)
        } else {
            fail("resultContent should be of type ${UnknownMessageEventContent::class} but was ${resultContent::class}")
        }
    }
}