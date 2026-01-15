package net.folivo.trixnity.clientserverapi.model.sync

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap.Companion.roomMapOf
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.default
import net.folivo.trixnity.core.serialization.events.invoke
import net.folivo.trixnity.core.serialization.events.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncResponseSerializerTest {
    @Test
    fun testSimpleSync() {
        val json = createMatrixEventJson()
        val serializer = SyncResponseSerializer(json, EventContentSerializerMappings.default)
        val value = json.decodeFromString(
            serializer,
            "{\"next_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"device_lists\":{\"changed\":[\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\"]},\"device_one_time_keys_count\":{\"signed_curve25519\":50},\"org.matrix.msc2732.device_unused_fallback_key_types\":[\"signed_curve25519\"],\"device_unused_fallback_key_types\":[\"signed_curve25519\"],\"rooms\":{\"join\":{\"!xxxxxxxxxxxxxxxx:matrix.org\":{\"timeline\":{\"events\":[{\"content\":{\"algorithm\":\"m.megolm.v1.aes-sha2\",\"ciphertext\":\"AwgGEoAB54CgH052RDgJQpaoo0El/sfLtVLURAQGGu6QUnEyjKUjRul80IZmZYsmwxTC2Bs6yDAc0EOWXN8o3vHWSfQsYYwTRkXlMTssVLKGUnqRQajsEtQT0kqNda6WvejITkuZmY2ThUAssVK5NjEGdtaT+vit//zaHG/XAm24Rs9NMcHObrwv/sbQHPt2htPiWVHLXQtakXyU810BIKNxUR5ILT4qUW/e6Z6eZUinKwKnpy2nWkJIJPki9o3zpGjMTKcq4e28j3X/vwc\",\"device_id\":\"TCXQVOAEIN\",\"sender_key\":\"Yy+JlhZtawC9oqLnOjFEUwzl/879kwyi8ivZ3u99a14\",\"session_id\":\"BGCwyqkDmnAe5L+dTw7wQNV8NPlPI2xoxHxwhfMMYtY\"},\"origin_server_ts\":1753141908323,\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.encrypted\",\"unsigned\":{\"membership\":\"join\",\"age\":254,\"transaction_id\":\"m1753141907962.0\"},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}],\"prev_batch\":\"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"limited\":false},\"state\":{\"events\":[{\"content\":{\"avatar_url\":\"mxc://matrix.org/xxxxxxxxxxxxxxxx\",\"displayname\":\"justJanne\",\"membership\":\"join\"},\"origin_server_ts\":1748954082755,\"sender\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"state_key\":\"@xxxxxxxx-xxxxxxxxxxxx:matrix.org\",\"type\":\"m.room.member\",\"unsigned\":{\"replaces_state\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\",\"age\":4187825822},\"event_id\":\"\$xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\"}]},\"account_data\":{\"events\":[]},\"ephemeral\":{\"events\":[]},\"unread_notifications\":{\"notification_count\":0,\"highlight_count\":0},\"summary\":{}}}}}"
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
            stateOf<CustomStateEventContent>("net.folivo.custom")
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
        val syncResponseString =
            $$"""{"next_batch":"next","rooms":{"join":{"!room":{"state":{"events":[{"content":{"foo":"foo"},"event_id":"$event","origin_server_ts":1234,"room_id":"!room","sender":"@sender:sender.org","state_key":"bar","type":"net.folivo.custom"}]}}}}}"""
        assertEquals(
            actual = json.encodeToString(serializer, syncResponse),
            expected = syncResponseString,
        )
        assertEquals(
            actual = json.decodeFromString(serializer, syncResponseString),
            expected = syncResponse,
        )
    }
}
