package de.connect2x.trixnity.core.serialization.events

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import de.connect2x.trixnity.core.model.events.ExtensibleEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks

private val log = Logger(" de.connect2x.trixnity.core.serialization.events.ExtensibleEventContent")

open class ExtensibleEventContentSerializer<L : ExtensibleEventContent.Legacy, C : ExtensibleEventContent<L>>(
    val name: String,
    val contentFactory: (blocks: EventContentBlocks, legacy: L?) -> C,
    val legacySerializer: KSerializer<L>,
) : KSerializer<C> {
    companion object {
        inline operator fun <reified C : ExtensibleEventContent<ExtensibleEventContent.Legacy.None>> invoke(
            crossinline contentFactory: (blocks: EventContentBlocks) -> C
        ): ExtensibleEventContentSerializer<ExtensibleEventContent.Legacy.None, C> =
            ExtensibleEventContentSerializer(
                C::class.simpleName ?: "ExtensibleEventContent",
                { b, _ -> contentFactory(b) },
                ExtensibleEventContent.Legacy.None.serializer()
            )

        inline operator fun <reified L : ExtensibleEventContent.Legacy, reified C : ExtensibleEventContent<L>> invoke(
            noinline contentFactory: (blocks: EventContentBlocks, legacy: L?) -> C
        ): ExtensibleEventContentSerializer<L, C> =
            ExtensibleEventContentSerializer(
                C::class.simpleName ?: "ExtensibleEventContent",
                contentFactory,
                serializer()
            )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name)
    override fun deserialize(decoder: Decoder): C {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val blocks =
            decoder.json.decodeFromJsonElement<EventContentBlocks>(JsonObject(jsonObject - legacySerializer.descriptor.elementNames.toSet()))
        val legacy = try {
            decoder.json.decodeFromJsonElement(legacySerializer, jsonObject)
        } catch (s: Exception) {
            log.warn(s) { "could not deserialize legacy content in $name" }
            null
        }
        return contentFactory(blocks, legacy)
    }

    override fun serialize(encoder: Encoder, value: C) {
        require(encoder is JsonEncoder)
        val blocks = encoder.json.encodeToJsonElement(value.blocks).jsonObject
        val legacyValue = value.legacy
        val legacy =
            if (legacyValue == null) emptyMap()
            else encoder.json.encodeToJsonElement(legacySerializer, legacyValue).jsonObject
        encoder.encodeJsonElement(JsonObject(blocks + legacy))
    }
}