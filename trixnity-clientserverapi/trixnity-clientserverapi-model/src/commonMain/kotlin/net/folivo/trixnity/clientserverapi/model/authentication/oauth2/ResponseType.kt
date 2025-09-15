package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ResponseTypeSerializer : KSerializer<ResponseType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseType", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): ResponseType = when (val value = decoder.decodeString().lowercase()) {
        "code" -> ResponseType.Code
        else -> ResponseType.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: ResponseType) = encoder.encodeString(value.toString())
}


@Serializable(with = ResponseTypeSerializer::class)
sealed interface ResponseType {
    object Code : ResponseType {
        override fun toString(): String = "code"
    }

    data class Unknown(private val value: String) : ResponseType {
        override fun toString(): String = value
    }
}
