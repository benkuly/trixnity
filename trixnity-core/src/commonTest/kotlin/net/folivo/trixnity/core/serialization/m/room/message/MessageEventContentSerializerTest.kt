package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.encodeToString
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContentSerializer
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageEventContentSerializerTest {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerialize() {
        val result = json.encodeToString(
            RoomMessageEventContentSerializer,
            RoomMessageEventContent.TextBased.Notice("test")
        )
        assertEquals("""{"body":"test","msgtype":"m.notice"}""", result)
    }

    @Test
    fun shouldDeserialize() {
        val input = """{"body":"test","format":null,"formatted_body":null,"msgtype":"m.text"}"""
        val result = json.decodeFromString(RoomMessageEventContentSerializer, input)
        assertEquals(RoomMessageEventContent.TextBased.Text("test"), result)
    }

    @Test
    fun shouldSerializeSubtype() {
        val result = json.encodeToString<RoomMessageEventContent>(RoomMessageEventContent.TextBased.Notice("test"))
        assertEquals("""{"body":"test","msgtype":"m.notice"}""", result)
    }

    @Test
    fun shouldDeserializeSubtype() {
        val input = """{"body":"test","format":null,"formatted_body":null,"msgtype":"m.text"}"""
        val result = json.decodeFromString<RoomMessageEventContent.TextBased.Text>(input)
        assertEquals(RoomMessageEventContent.TextBased.Text("test"), result)
    }
}