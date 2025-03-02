package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable(with = SignedSerializer::class)
open class Signed<T, U>(
    open val signed: T,
    open val signatures: Signatures<U>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as Signed<*, *>

        if (signed != other.signed) return false
        if (signatures != other.signatures) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signed?.hashCode() ?: 0
        result = 31 * result + signatures.hashCode()
        return result
    }

    override fun toString(): String {
        return "Signed(signed=$signed, signatures=$signatures)"
    }
}

class SignedSerializer<T, U>(
    private val valueSerializer: KSerializer<T>,
    private val signaturesKeySerializer: KSerializer<U>
) : KSerializer<Signed<T, U>> {
    override fun deserialize(decoder: Decoder): Signed<T, U> {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        val signaturesSerializer = MapSerializer(signaturesKeySerializer, KeysSerializer)
        val signatures = jsonObj["signatures"]?.let {
            if (it is JsonObject) decoder.json.decodeFromJsonElement(signaturesSerializer, it)
            else null
        }
        return Signed(
            signed = decoder.json.decodeFromJsonElement(valueSerializer, jsonObj),
            signatures = signatures
        )
    }

    override fun serialize(encoder: Encoder, value: Signed<T, U>) {
        require(encoder is JsonEncoder)
        val signedValue = encoder.json.encodeToJsonElement(valueSerializer, value.signed)
        val signaturesSerializer = MapSerializer(signaturesKeySerializer, KeysSerializer)
        require(signedValue is JsonObject)
        encoder.encodeJsonElement(
            JsonObject(buildMap {
                putAll(signedValue)
                value.signatures?.let { put("signatures", encoder.json.encodeToJsonElement(signaturesSerializer, it)) }
            })
        )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Signed")
}