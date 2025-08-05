package net.folivo.trixnity.clientserverapi.model.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.RoomId
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncAddRoomIdTest {
    @Test
    fun addsRoomIdToMessage() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\"},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\"},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}",
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToEdit() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"type\":\"m.room.message\",\"content\":{\"msgtype\":\"m.text\",\"body\":\"* Edit\",\"m.new_content\":{\"msgtype\":\"m.text\",\"body\":\"Edit\",\"m.mentions\":{}},\"m.mentions\":{},\"m.relates_to\":{\"rel_type\":\"m.replace\",\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}},\"event_id\":\"\$f7rUNQQk_euyvEmOIwUzLYV_1XPXmzX6N-GNsuub4qk\",\"user_id\":\"@janne-koschinski:matrix.org\",\"sender\":\"@janne-koschinski:matrix.org\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\",\"origin_server_ts\":1754390532635}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"type\":\"m.room.message\",\"content\":{\"msgtype\":\"m.text\",\"body\":\"* Edit\",\"m.new_content\":{\"msgtype\":\"m.text\",\"body\":\"Edit\",\"m.mentions\":{}},\"m.mentions\":{},\"m.relates_to\":{\"rel_type\":\"m.replace\",\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}},\"event_id\":\"\$f7rUNQQk_euyvEmOIwUzLYV_1XPXmzX6N-GNsuub4qk\",\"user_id\":\"@janne-koschinski:matrix.org\",\"sender\":\"@janne-koschinski:matrix.org\",\"origin_server_ts\":1754390532635}",
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToReply() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Reply\",\"m.mentions\":{},\"m.relates_to\":{\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390489981,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":1853,\"transaction_id\":\"m1754390489106.12\"},\"event_id\":\"\$6mmtLgBQ-Ae8l7NSsHKYkgtn8O6EOwKxSAKEE_PK0bY\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Reply\",\"m.mentions\":{},\"m.relates_to\":{\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390489981,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":1853,\"transaction_id\":\"m1754390489106.12\"},\"event_id\":\"\$6mmtLgBQ-Ae8l7NSsHKYkgtn8O6EOwKxSAKEE_PK0bY\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToEmptyThreadRoot() {
        // Missing
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
        // Null
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":null,\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":null,\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
        // Undefined
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":undefined,\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":undefined,\"count\":0,\"current_user_participated\":false}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToThreadRoot() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":{\"content\":{\"body\":\"Thread\",\"m.mentions\":{},\"m.relates_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"is_falling_back\":true,\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"rel_type\":\"m.thread\"},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390493121,\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\",\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"age\":564,\"transaction_id\":\"m1754390492376.13\"},\"event_id\":\"\$dkt-fE304ytkg_nUf-jEN9eCpA9rUZ8iOGFgxbTyroY\",\"user_id\":\"@janne-koschinski:matrix.org\",\"age\":564},\"count\":1,\"current_user_participated\":true}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"m.thread\":{\"latest_event\":{\"content\":{\"body\":\"Thread\",\"m.mentions\":{},\"m.relates_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"is_falling_back\":true,\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"rel_type\":\"m.thread\"},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390493121,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"age\":564,\"transaction_id\":\"m1754390492376.13\"},\"event_id\":\"\$dkt-fE304ytkg_nUf-jEN9eCpA9rUZ8iOGFgxbTyroY\",\"user_id\":\"@janne-koschinski:matrix.org\",\"age\":564},\"count\":1,\"current_user_participated\":true}}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToThreadMessage() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Thread\",\"m.mentions\":{},\"m.relates_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"is_falling_back\":true,\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"rel_type\":\"m.thread\"},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390493121,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":564,\"transaction_id\":\"m1754390492376.13\"},\"event_id\":\"\$dkt-fE304ytkg_nUf-jEN9eCpA9rUZ8iOGFgxbTyroY\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Thread\",\"m.mentions\":{},\"m.relates_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"is_falling_back\":true,\"m.in_reply_to\":{\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"rel_type\":\"m.thread\"},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390493121,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":564,\"transaction_id\":\"m1754390492376.13\"},\"event_id\":\"\$dkt-fE304ytkg_nUf-jEN9eCpA9rUZ8iOGFgxbTyroY\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToRedactedMessage() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":911,\"transaction_id\":\"m1754390739783.15\",\"redacted_because\":{\"content\":{\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"origin_server_ts\":1754391184760,\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.redaction\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\"},\"event_id\":\"\$DPV7jd4H4Q4CMD26W-6UIvmB_uUbyy26mToMGuYAr2k\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":911,\"transaction_id\":\"m1754390739783.15\",\"redacted_because\":{\"content\":{\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"origin_server_ts\":1754391184760,\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.redaction\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\"},\"event_id\":\"\$DPV7jd4H4Q4CMD26W-6UIvmB_uUbyy26mToMGuYAr2k\"}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToRedaction() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"origin_server_ts\":1754391184760,\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.redaction\",\"unsigned\":{\"membership\":\"join\",\"age\":386,\"transaction_id\":\"m1754391184606.16\"},\"event_id\":\"\$DPV7jd4H4Q4CMD26W-6UIvmB_uUbyy26mToMGuYAr2k\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"},\"origin_server_ts\":1754391184760,\"redacts\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.redaction\",\"unsigned\":{\"membership\":\"join\",\"age\":386,\"transaction_id\":\"m1754391184606.16\"},\"event_id\":\"\$DPV7jd4H4Q4CMD26W-6UIvmB_uUbyy26mToMGuYAr2k\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToMessageWithReaction() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"org.matrix.msc2675.annotation\": [{\"key\":\"👍\",\"origin_server_ts\":1754392124424,\"count\":1}]}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"body\":\"Message\",\"m.mentions\":{},\"msgtype\":\"m.text\"},\"origin_server_ts\":1754390485300,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.room.message\",\"unsigned\":{\"membership\":\"join\",\"age\":8385,\"transaction_id\":\"m1754390484474.11\",\"m.relations\":{\"org.matrix.msc2675.annotation\": [{\"key\":\"👍\",\"origin_server_ts\":1754392124424,\"count\":1}]}},\"event_id\":\"\$osCnOKPfvz3RurR7MsIw7IAOUMTHRS7e4OUakktZlz0\"}"
                ) as JsonObject
            )
        )
    }

    @Test
    fun addsRoomIdToReaction() {
        assertEquals(
            expected = Json.parseToJsonElement(
                "{\"content\":{\"m.relates_to\":{\"event_id\":\"\$-jJeIc76jw5AXMEsTY3p_nn3vSs7mtavdWOkA0aUZ_0\",\"key\":\"👍️\",\"rel_type\":\"m.annotation\"}},\"origin_server_ts\":1754392124424,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.reaction\",\"unsigned\":{\"membership\":\"join\",\"age\":755,\"transaction_id\":\"m1754392124307.20\"},\"event_id\":\"\$J6XPX4JxrFBTXpvnw-_hC-WE-STfr6HbBdi8bvajbRY\",\"room_id\":\"!kEPxfTHRGAIEMjppiX:matrix.org\"}"
            ),
            actual = Sync.addRoomIdToEvent(
                roomId = RoomId("!kEPxfTHRGAIEMjppiX:matrix.org"),
                event = Json.parseToJsonElement(
                    "{\"content\":{\"m.relates_to\":{\"event_id\":\"\$-jJeIc76jw5AXMEsTY3p_nn3vSs7mtavdWOkA0aUZ_0\",\"key\":\"👍️\",\"rel_type\":\"m.annotation\"}},\"origin_server_ts\":1754392124424,\"sender\":\"@janne-koschinski:matrix.org\",\"type\":\"m.reaction\",\"unsigned\":{\"membership\":\"join\",\"age\":755,\"transaction_id\":\"m1754392124307.20\"},\"event_id\":\"\$J6XPX4JxrFBTXpvnw-_hC-WE-STfr6HbBdi8bvajbRY\"}"
                ) as JsonObject
            )
        )
    }
}
