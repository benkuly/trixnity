package de.connect2x.trixnity.core.serialization.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import de.connect2x.trixnity.core.model.events.UnknownEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.serialization.canonicalJson

class UnknownEventContentSerializer(val eventType: String) : KSerializer<UnknownEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownEventContent")

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): UnknownEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val contextualEventContentBlocksSerializer =
            decoder.json.serializersModule.getContextual(EventContentBlocks::class)
        val blocks =
            if (contextualEventContentBlocksSerializer == null) EventContentBlocks()
            else decoder.json.decodeFromJsonElement(
                contextualEventContentBlocksSerializer,
                jsonObject
            )
        return UnknownEventContent(jsonObject, blocks, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(canonicalJson(value.raw))
    }
}