package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.crypto.Signed

class SignedSerializer<T, U>(
    private val valueSerializer: KSerializer<T>,
    private val signaturesKeySerializer: KSerializer<U>
) : KSerializer<Signed<T, U>> {
    override fun deserialize(decoder: Decoder): Signed<T, U> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val signatures = jsonObj["signatures"]
        require(signatures != null && signatures is JsonObject)
        val signaturesSerializer = MapSerializer(signaturesKeySerializer, KeysSerializer)
        return Signed(
            signed = decoder.json.decodeFromJsonElement(valueSerializer, jsonObj),
            signatures = decoder.json.decodeFromJsonElement(signaturesSerializer, signatures)
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun serialize(encoder: Encoder, value: Signed<T, U>) {
        require(encoder is JsonEncoder)
        val signedValue = encoder.json.encodeToJsonElement(valueSerializer, value.signed)
        val signaturesSerializer = MapSerializer(signaturesKeySerializer, KeysSerializer)
        val signatures = encoder.json.encodeToJsonElement(signaturesSerializer, value.signatures)
        require(signedValue is JsonObject)
        encoder.encodeJsonElement(
            JsonObject(buildMap {
                putAll(signedValue)
                put("signatures", signatures)
            })
        )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Signed")
}