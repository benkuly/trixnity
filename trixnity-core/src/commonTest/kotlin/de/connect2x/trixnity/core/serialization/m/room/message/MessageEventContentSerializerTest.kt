package de.connect2x.trixnity.core.serialization.m.room.message

import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    @Test
    fun shouldSerialize() {
        val result = json.encodeToString(
            RoomMessageEventContent.Serializer,
            RoomMessageEventContent.TextBased.Notice("test")
        )
        assertEquals("""{"body":"test","msgtype":"m.notice"}""", result)
    }

    @Test
    fun shouldDeserialize() {
        val input = """{"body":"test","format":null,"formatted_body":null,"msgtype":"m.text"}"""
        val result = json.decodeFromString(RoomMessageEventContent.Serializer, input)
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