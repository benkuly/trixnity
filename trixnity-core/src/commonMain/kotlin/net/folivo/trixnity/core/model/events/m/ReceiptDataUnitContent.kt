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
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#receipts">matrix spec</a>
 */
@Serializable(with = ReceiptDataUnitContentSerializer::class)
data class ReceiptDataUnitContent(
    val receipts: Map<String, RoomReceipts>,
) : EphemeralDataUnitContent {
    @Serializable
    data class RoomReceipts(
        @SerialName("m.read")
        val read: UserReadReceipt
    ) {
        @Serializable
        data class UserReadReceipt(
            @SerialName("data")
            val data: ReadReceiptMetadata,
            @SerialName("event_ids")
            val eventIds: Set<EventId>
        ) {
            @Serializable
            data class ReadReceiptMetadata(
                @SerialName("ts")
                val ts: Long
            )
        }
    }
}

object ReceiptDataUnitContentSerializer : KSerializer<ReceiptDataUnitContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReceiptDataUnitContentSerializer")

    override fun deserialize(decoder: Decoder): ReceiptDataUnitContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        return ReceiptDataUnitContent(decoder.json.decodeFromJsonElement(jsonObject))
    }

    override fun serialize(encoder: Encoder, value: ReceiptDataUnitContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value.receipts))
    }
}