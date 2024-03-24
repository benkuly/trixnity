package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#receipts">matrix spec</a>
 */
@Serializable(with = ReceiptEventContentSerializer::class)
data class ReceiptEventContent(
    val events: Map<EventId, Map<ReceiptType, Map<UserId, Receipt>>>
) : EphemeralEventContent {
    @Serializable
    data class Receipt(@SerialName("ts") val timestamp: Long)
}

object ReceiptEventContentSerializer : KSerializer<ReceiptEventContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReceiptEventContentSerializer")

    override fun deserialize(decoder: Decoder): ReceiptEventContent {
        require(decoder is JsonDecoder)
        return ReceiptEventContent(decoder.json.decodeFromJsonElement(decoder.decodeJsonElement()))
    }

    override fun serialize(encoder: Encoder, value: ReceiptEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value.events))
    }

}