package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent.Receipt

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#receipts">matrix spec</a>
 */
@Serializable(with = ReadEventsSerializer::class)
data class ReceiptEventContent(
    val events: Map<EventId, Set<Receipt>>
) : EphemeralEventContent {

    sealed class Receipt {
        data class ReadReceipt(
            val read: Map<UserId, ReadEvent>
        ) : Receipt() {
            @Serializable
            data class ReadEvent(@SerialName("ts") val timestamp: Long)
        }

        data class Unknown(
            val raw: JsonElement,
            val type: String
        ) : Receipt()
    }
}

object ReadEventsSerializer : KSerializer<ReceiptEventContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ReadEventsSerializer")

    override fun deserialize(decoder: Decoder): ReceiptEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return ReceiptEventContent(jsonObject.entries.associate { (eventId, jsonObject) ->
            EventId(eventId) to jsonObject.jsonObject.entries.map { (type, receipt) ->
                when (type) {
                    "m.read" -> Receipt.ReadReceipt(decoder.json.decodeFromJsonElement(receipt))
                    else -> Receipt.Unknown(raw = receipt, type = type)
                }
            }.toSet()
        })
    }

    override fun serialize(encoder: Encoder, value: ReceiptEventContent) {
        require(encoder is JsonEncoder)
        val json = JsonObject(value.events.entries.associate { (eventId, receipts) ->
            eventId.full to JsonObject(receipts.associate { receipt ->
                when (receipt) {
                    is Receipt.ReadReceipt -> "m.read" to encoder.json.encodeToJsonElement(receipt.read)
                    is Receipt.Unknown -> receipt.type to receipt.raw
                }
            })
        })
        encoder.encodeJsonElement(json)
    }

}