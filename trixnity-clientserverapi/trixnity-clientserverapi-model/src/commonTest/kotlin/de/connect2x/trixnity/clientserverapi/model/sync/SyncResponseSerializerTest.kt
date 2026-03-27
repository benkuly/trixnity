package de.connect2x.trixnity.clientserverapi.model.sync

import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap.Companion.roomMapOf
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.core.serialization.events.invoke
import de.connect2x.trixnity.core.serialization.events.stateOf
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncResponseSerializerTest {
    @Test
    fun testSimpleSync() {
        val json = createMatrixEventJson()
        val serializer = SyncResponseSerializer(json, EventContentSerializerMappings.default)
        val value = json.decodeFromString(
            serializer,
            "{\"next_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"device_lists\":{\"changed\":[\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\"]},\"device_one_time_keys_count\":{\"signed_curve25519\":50},\"device_unused_fallback_key_types\":[\"signed_curve25519\"],\"rooms\":{\"join\":{\"!xxxxxxxxxxxxxxxx:matrix.org\":{\"timeline\":{\"events\":[{\"content\":{\"algorithm\":\"m.megolm.v1.aes-sha2\",\"ciphertext\":\"AwgGEoAB54CgH052RDgJQpaoo0El/sfLtVLURAQGGu6QUnEyjKUjRul80IZmZYsmwxTC2Bs6yDAc0EOWXN8o3vHWSfQsYYwTRkXlMTssVLKGUnqRQajsEtQT0kqNda6WvejITkuZmY2ThUAssVK5NjEGdtaT+vit//zaHG/XAm24Rs9NMcHObrwv/sbQHPt2htPiWVHLXQtakXyU810BIKNxUR5ILT4qUW/e6Z6eZUinKwKnpy2nWkJIJPki9o3zpGjMTKcq4e28j3X/vwc\",\"device_id\":\"TCXQVOAEIN\",\"sender_key\":\"Yy+JlhZtawC9oqLnOjFEUwzl/879kwyi8ivZ3u99a14\",\"session_id\":\"BGCwyqkDmnAe5L+dTw7wQNV8NPlPI2xoxHxwhfMMYtY\"},\"origin_server_ts\":1753141908323,\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.encrypted\",\"unsigned\":{\"membership\":\"join\",\"age\":254,\"transaction_id\":\"m1753141907962.0\"},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}],\"prev_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"limited\":false},\"state\":{\"events\":[{\"content\":{\"avatar_url\":\"mxc://matrix.org/xxxxxxxxxxxxxxxx\",\"displayname\":\"justJanne\",\"membership\":\"join\"},\"origin_server_ts\":1748954082755,\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"state_key\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.member\",\"unsigned\":{\"replaces_state\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"age\":4187825822},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}]},\"account_data\":{\"events\":[]},\"ephemeral\":{\"events\":[]},\"unread_notifications\":{\"notification_count\":0,\"highlight_count\":0},\"summary\":{}}}}}"
        )
        assertEquals(
            actual = json.encodeToString(serializer, value),
            expected = "{\"next_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"rooms\":{\"join\":{\"!xxxxxxxxxxxxxxxx:matrix.org\":{\"summary\":{},\"state\":{\"events\":[{\"content\":{\"avatar_url\":\"mxc://matrix.org/xxxxxxxxxxxxxxxx\",\"displayname\":\"justJanne\",\"membership\":\"join\"},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"origin_server_ts\":1748954082755,\"room_id\":\"!xxxxxxxxxxxxxxxx:matrix.org\",\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"state_key\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.member\",\"unsigned\":{\"age\":4187825822}}]},\"timeline\":{\"events\":[{\"content\":{\"algorithm\":\"m.megolm.v1.aes-sha2\",\"ciphertext\":\"AwgGEoAB54CgH052RDgJQpaoo0El/sfLtVLURAQGGu6QUnEyjKUjRul80IZmZYsmwxTC2Bs6yDAc0EOWXN8o3vHWSfQsYYwTRkXlMTssVLKGUnqRQajsEtQT0kqNda6WvejITkuZmY2ThUAssVK5NjEGdtaT+vit//zaHG/XAm24Rs9NMcHObrwv/sbQHPt2htPiWVHLXQtakXyU810BIKNxUR5ILT4qUW/e6Z6eZUinKwKnpy2nWkJIJPki9o3zpGjMTKcq4e28j3X/vwc\",\"device_id\":\"TCXQVOAEIN\",\"sender_key\":\"Yy+JlhZtawC9oqLnOjFEUwzl/879kwyi8ivZ3u99a14\",\"session_id\":\"BGCwyqkDmnAe5L+dTw7wQNV8NPlPI2xoxHxwhfMMYtY\"},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"origin_server_ts\":1753141908323,\"room_id\":\"!xxxxxxxxxxxxxxxx:matrix.org\",\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.encrypted\",\"unsigned\":{\"age\":254,\"membership\":\"join\",\"transaction_id\":\"m1753141907962.0\"}}],\"limited\":false,\"prev_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"},\"ephemeral\":{\"events\":[]},\"account_data\":{\"events\":[]},\"unread_notifications\":{\"highlight_count\":0,\"notification_count\":0}}}},\"device_lists\":{\"changed\":[\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\"]},\"device_one_time_keys_count\":{\"signed_curve25519\":50},\"device_unused_fallback_key_types\":[\"signed_curve25519\"]}",
        )
    }

    @Serializable
    private data class CustomStateEventContent(
        val foo: String
    ) : StateEventContent {
        override val externalUrl: String? = null
    }

    @Test
    fun supportCustomEvents() {
        val mappings = EventContentSerializerMappings.default + EventContentSerializerMappings {
            stateOf<CustomStateEventContent>("de.connect2x.custom")
        }
        val json = createMatrixEventJson(mappings)
        val serializer = SyncResponseSerializer(json, mappings)
        val syncResponse = Sync.Response(
            nextBatch = "next",
            room = Sync.Response.Rooms(
                join = roomMapOf(
                    RoomId("!room") to Sync.Response.Rooms.JoinedRoom(
                        state = Sync.Response.Rooms.State(
                            events = listOf(
                                ClientEvent.RoomEvent.StateEvent(
                                    content = CustomStateEventContent("foo"),
                                    id = EventId("\$event"),
                                    sender = UserId("@sender:sender.org"),
                                    roomId = RoomId("!room"),
                                    stateKey = "bar",
                                    originTimestamp = 1234,
                                )
                            )
                        )
                    )
                )
            )
        )
        val syncResponseString = """
                        {
                            "next_batch": "next",
                            "rooms": {
                                "join": {
                                    "!room": {
                                        "state": {
                                            "events": [
                                                {
                                                    "content": { "foo": "foo" },
                                                    "event_id": "${'$'}event",
                                                    "origin_server_ts": 1234,
                                                    "room_id": "!room",
                                                    "sender": "@sender:sender.org",
                                                    "state_key": "bar",
                                                    "type": "de.connect2x.custom"
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                """.trimIndent()
        assertEquals(
            actual = json.parseToJsonElement(json.encodeToString(serializer, syncResponse)),
            expected = json.parseToJsonElement(syncResponseString),
        )
        assertEquals(
            actual = json.decodeFromString(serializer, syncResponseString),
            expected = syncResponse,
        )
    }

    @Test
    @OptIn(MSC4354::class)
    fun supportStickyEventsSectionWithUnstableAliases() {
        val json = createMatrixEventJson()
        val serializer = SyncResponseSerializer(json, EventContentSerializerMappings.default)

        val stickyAliasPayload = """
                        {
                            "next_batch": "next",
                            "rooms": {
                                "join": {
                                    "!room": {
                                        "sticky": {
                                            "events": [
                                                {
                                                    "type": "m.room.message",
                                                    "sender": "@sender:sender.org",
                                                    "origin_server_ts": 1234,
                                                    "event_id": "${'$'}event",
                                                    "room_id": "!room",
                                                    "content": {
                                                        "msgtype": "m.text",
                                                        "body": "hi",
                                                        "m.mentions": {}
                                                    },
                                                    "sticky": { "duration_ms": 60000 }
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                """.trimIndent()

        val mscAliasPayload = """
                        {
                            "next_batch": "next",
                            "rooms": {
                                "join": {
                                    "!room": {
                                        "msc4354_sticky": {
                                            "events": [
                                                {
                                                    "type": "m.room.message",
                                                    "sender": "@sender:sender.org",
                                                    "origin_server_ts": 1234,
                                                    "event_id": "${'$'}event",
                                                    "room_id": "!room",
                                                    "content": {
                                                        "msgtype": "m.text",
                                                        "body": "hi",
                                                        "m.mentions": {}
                                                    },
                                                    "msc4354_sticky": { "duration_ms": 60000 }
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                """.trimIndent()

        val decodedStickyAlias = json.decodeFromString(serializer, stickyAliasPayload)
        val joinedStickyAlias = decodedStickyAlias.room?.join?.get(RoomId("!room"))
        requireNotNull(joinedStickyAlias)
        val stickyEvents1 = joinedStickyAlias.sticky?.events
        requireNotNull(stickyEvents1)
        require(stickyEvents1.size == 1)
        require(stickyEvents1.single().sticky?.durationMs == 60000L)

        val decodedMscAlias = json.decodeFromString(serializer, mscAliasPayload)
        val joinedMscAlias = decodedMscAlias.room?.join?.get(RoomId("!room"))
        requireNotNull(joinedMscAlias)
        val stickyEvents2 = joinedMscAlias.sticky?.events
        requireNotNull(stickyEvents2)
        require(stickyEvents2.size == 1)
        require(stickyEvents2.single().sticky?.durationMs == 60000L)
    }
}
