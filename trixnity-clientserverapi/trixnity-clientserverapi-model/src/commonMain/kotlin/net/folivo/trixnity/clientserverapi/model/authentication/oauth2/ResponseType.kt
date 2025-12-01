package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ResponseTypeSerializer::class)
sealed interface ResponseType {
    val value: String

    object Code : ResponseType {
        override val value: String = "code"
    }

    data class Unknown(override val value: String) : ResponseType
}

object ResponseTypeSerializer : KSerializer<ResponseType> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ResponseType = when (val value = decoder.decodeString().lowercase()) {
        ResponseType.Code.value -> ResponseType.Code
        else -> ResponseType.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: ResponseType) = encoder.encodeString(value.value)
}