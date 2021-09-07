package net.folivo.trixnity.core.serialization.m.room.encrypted

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.*
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

object EncryptedEventContentSerializer : KSerializer<EncryptedEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): EncryptedEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (decoder.json.decodeFromJsonElement<EncryptionAlgorithm>(
            jsonObj["algorithm"] ?: JsonPrimitive("unknown")
        )) {
            Olm ->
                decoder.json.decodeFromJsonElement<OlmEncryptedEventContent>(jsonObj)
            Megolm ->
                decoder.json.decodeFromJsonElement<MegolmEncryptedEventContent>(jsonObj)
            else ->
                decoder.json.decodeFromJsonElement<UnknownEncryptedEventContent>(jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptedEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is OlmEncryptedEventContent ->
                encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(OlmEncryptedEventContentSerializer, "algorithm" to Olm.name), value
                )
            is MegolmEncryptedEventContent ->
                encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(MegolmEncryptedEventContentSerializer, "algorithm" to Megolm.name), value
                )
            is UnknownEncryptedEventContent -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object MegolmEncryptedEventContentSerializer :
    AddFieldsSerializer<MegolmEncryptedEventContent>(
        MegolmEncryptedEventContent.serializer(),
        "algorithm" to Megolm.name
    )

object OlmEncryptedEventContentSerializer :
    AddFieldsSerializer<OlmEncryptedEventContent>(
        OlmEncryptedEventContent.serializer(),
        "algorithm" to Olm.name
    )