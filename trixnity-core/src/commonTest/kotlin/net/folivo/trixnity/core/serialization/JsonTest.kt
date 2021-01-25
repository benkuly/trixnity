package net.folivo.trixnity.core.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.UnknownEventContent
import net.folivo.trixnity.core.model.events.UnsignedData
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.DefaultMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class JsonTest {
    private val json = createJson()

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
        val result = json.decodeFromString<Event<EventContent>>(content)
        val resultContent = result.content
        if (result is RoomEvent && resultContent is TextMessageEventContent) {
            assertEquals("org.matrix.custom.html", resultContent.format)
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
        val result = json.decodeFromString<Event<*>>(content)
        val resultContent = result.content
        if (result is RoomEvent && resultContent is DefaultMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
        } else {
            fail("resultContent should be of type ${DefaultMessageEventContent::class}")
        }
    }

    @Test
    fun shouldSerializeEventList() {
        val content = listOf(
            StateEvent(
                id = EventId("event1", "server"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedData(),
                originTimestamp = 12341,
                sender = UserId("sender", "server"),
                content = NameEventContent(),
                stateKey = ""
            ),
            StateEvent(
                id = EventId("event2", "server"),
                roomId = RoomId("room", "server"),
                unsigned = UnsignedData(),
                originTimestamp = 12342,
                sender = UserId("sender", "server"),
                stateKey = UserId("user", "server").full,
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
        val result = json.decodeFromString<StateEvent<CanonicalAliasEventContent>>(content)
        assertEquals(RoomAliasId("somewhere", "example.org"), result.content.alias)
        assertEquals(1234, result.unsigned?.age)
    }

    @Test
    fun shouldCreateSubtypeFromRoomEvent() {
        val content = """
        {
            "content": {
                "reason":"bob"
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
        val result = json.decodeFromString<RoomEvent<RedactionEventContent>>(content)
        assertEquals("bob", result.content.reason)
        assertEquals(1234, result.unsigned?.age)
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
        val result = json.decodeFromString<Event<*>>(content)
        if (result is UnknownEvent) {
            assertEquals("unknownEventType", result.type)
            assertEquals(UnknownEventContent(), result.content)
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
        val result = json.decodeFromString<List<Event<out EventContent>>>(content)
        val result1 = result[0]
        val result2 = result[1]
        if (result1 is UnknownEvent && result2 is UnknownEvent) {
            assertEquals("unknownEventType1", result1.type)
            assertEquals("unknownEventType2", result2.type)
            assertEquals(UnknownEventContent(), result1.content)
            assertEquals(UnknownEventContent(), result2.content)
        } else {
            fail("result should be of type ${UnknownEvent::class}")
        }
    }

    @Serializable
    data class CustomResponse(
        @SerialName("event") val event: RoomEvent<*>
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
        if (resultContent is DefaultMessageEventContent) {
            assertEquals("This is an example text message", resultContent.body)
            assertEquals("dino", resultContent.type)
        } else {
            fail("resultContent should be of type ${DefaultMessageEventContent::class}")
        }
    }
}