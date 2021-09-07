package net.folivo.trixnity.core.serialization.m.room.message

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.NoticeMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.MessageEventContent.TextMessageEventContent
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageEventContentSerializerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = createMessageEventContentSerializersModule()
    }

    @Test
    fun shouldSerialize() {
        val result = json.encodeToString(
            MessageEventContentSerializer,
            NoticeMessageEventContent("test")
        )
        assertEquals("""{"body":"test","msgtype":"m.notice"}""", result)
    }

    @Test
    fun shouldDeserialize() {
        val input = """{"body":"test","format":null,"formatted_body":null,"msgtype":"m.text"}"""
        val result = json.decodeFromString(MessageEventContentSerializer, input)
        assertEquals(TextMessageEventContent("test"), result)
    }

    @Test
    fun shouldSerializeSubtype() {
        val result = json.encodeToString<MessageEventContent>(NoticeMessageEventContent("test"))
        assertEquals("""{"body":"test","msgtype":"m.notice"}""", result)
    }

    @Test
    fun shouldDeserializeSubtype() {
        val input = """{"body":"test","format":null,"formatted_body":null,"msgtype":"m.text"}"""
        val result = json.decodeFromString<TextMessageEventContent>(input)
        assertEquals(TextMessageEventContent("test"), result)
    }
}