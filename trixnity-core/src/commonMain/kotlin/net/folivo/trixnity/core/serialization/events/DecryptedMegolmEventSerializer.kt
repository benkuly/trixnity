package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.folivo.trixnity.core.model.events.DecryptedMegolmEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer
import net.folivo.trixnity.core.serialization.canonicalJson

private val log = KotlinLogging.logger {}

class DecryptedMegolmEventSerializer(
    private val messageEventContentSerializers: Set<SerializerMapping<out MessageEventContent>>,
    private val messageEventContentSerializer: MessageEventContentSerializer,
) : KSerializer<DecryptedMegolmEvent<*>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DecryptedMegolmEventSerializer")

    override fun deserialize(decoder: Decoder): DecryptedMegolmEvent<*> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: throw SerializationException("type must not be null")

        val contentSerializer = MessageEventContentSerializer(messageEventContentSerializers, type)
        return decoder.json.tryDeserializeOrElse(DecryptedMegolmEvent.serializer(contentSerializer), jsonObj) {
            log.warn(it) { "could not deserialize event: $jsonObj" }
            DecryptedMegolmEvent.serializer(UnknownRoomEventContentSerializer(type))
        }
    }

    override fun serialize(encoder: Encoder, value: DecryptedMegolmEvent<*>) {
        require(encoder is JsonEncoder)
        val content = value.content
        val type = messageEventContentSerializers.contentType(content)

        val jsonElement = encoder.json.encodeToJsonElement(
            @Suppress("UNCHECKED_CAST")
            AddFieldsSerializer(
                DecryptedMegolmEvent.serializer(messageEventContentSerializer) as KSerializer<DecryptedMegolmEvent<*>>,
                "type" to type
            ), value
        )
        encoder.encodeJsonElement(canonicalJson(jsonElement))
    }
}