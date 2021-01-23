package net.folivo.trixnity.core.serialization

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.UnknownEvent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEvent.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MemberEvent.MemberUnsignedData
import net.folivo.trixnity.core.model.events.m.room.MessageEvent.MessageEventContent.*
import net.folivo.trixnity.core.model.events.m.room.NameEvent.NameEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EventSerializerTest {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = createEventSerializersModule()
    }

    @Test
    fun shouldSerializeEvent() {
        val content = CanonicalAliasEvent(
            CanonicalAliasEventContent(RoomAliasId("somewhere", "example.org")),
            UserId("example", "example.org"),
            EventId("143273582443PhrSn", "example.org"),
            1432735824653,
            StandardUnsignedData(1234),
            RoomId("jEsUZKDJdhlrceRyVU", "example.org"),
        )
        val expectedResult = """
        {
            "content":{
                "alias":"#somewhere:example.org",
                "alt_aliases":[]
            },
            "sender":"@example:example.org",
            "event_id":"$143273582443PhrSn:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234,"redactedBecause":null,"transaction_id":null},
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "prev_content":null,
            "state_key":"",
            "type":"m.room.canonical_alias"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldSerializeEventList() {
        val content = listOf(
            NameEvent(
                id = EventId("event1", "server"),
                roomId = RoomId("room", "server"),
                unsigned = StandardUnsignedData(),
                originTimestamp = 12341,
                sender = UserId("sender", "server"),
                content = NameEventContent()
            ),
            MemberEvent(
                id = EventId("event2", "server"),
                roomId = RoomId("room", "server"),
                unsigned = MemberUnsignedData(),
                originTimestamp = 12342,
                sender = UserId("sender", "server"),
                relatedUser = UserId("user", "server"),
                content = MemberEventContent(membership = INVITE)
            )
        )
        val expectedResult = """
        {
            "content":{
                "alias":"#somewhere:example.org",
                "alt_aliases":[]
            },
            "sender":"@example:example.org",
            "event_id":"$143273582443PhrSn:example.org",
            "origin_server_ts":1432735824653,
            "unsigned":{"age":1234,"redactedBecause":null,"transaction_id":null},
            "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
            "prev_content":null,
            "state_key":"",
            "type":"m.room.canonical_alias"
        }
    """.trimIndent().lines().joinToString("") { it.trim() }
        val result = json.encodeToString(listOf(content, content))
        assertEquals(expectedResult, result)
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
        val result = json.decodeFromString<CanonicalAliasEvent>(content)
        assertEquals(
            RoomAliasId("somewhere", "example.org"), result.content.alias
        )
        assertEquals(1234, result.unsigned.age)
        assertEquals("m.room.canonical_alias", result.type)
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
        val result = json.decodeFromString<CreateEvent>(content)
        assertEquals(UserId("example", "example.org"), result.content.creator)
        assertEquals(1234, result.unsigned.age)
        assertEquals("m.room.create", result.type)
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
        val result: Event<*> = json.decodeFromString<Event<Any>>(content)
        if (result is UnknownEvent) {
            assertEquals("unknownEventType", result.type)
            assertEquals("unicorn", result.content["something"]?.jsonPrimitive?.content)
        } else {
            fail("result should be of type ${UnknownEvent::class}")
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
                    "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
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
                    "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                    "sender": "@example:example.org",
                    "type": "unknownEventType2",
                    "unsigned": {
                        "age": 1234
                    }
                }
        ]
    """.trimIndent()
        val result: List<Event<*>> = json.decodeFromString<List<Event<Any>>>(content)
        val result1 = result[0]
        val result2 = result[1]
        if (result1 is UnknownEvent && result2 is UnknownEvent) {
            assertEquals("unknownEventType1", result1.type)
            assertEquals("unknownEventType2", result2.type)
            assertEquals("unicorn1", result1.content["something"]?.jsonPrimitive?.content)
            assertEquals("unicorn2", result2.content["something"]?.jsonPrimitive?.content)
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
        val result: Event<*> = json.decodeFromString<Event<Any>>(content)
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
        val result: Event<*> = json.decodeFromString<Event<Any>>(content)
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
        val result: Event<*> = json.decodeFromString<Event<Any>>(content)
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
        val result = json.encodeToString(NoticeMessageEventContent("test"))
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
        val result = json.decodeFromString<CustomResponse>(content)
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