package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private class ResponseModeSerializer : KSerializer<ResponseMode> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseMode", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): ResponseMode = when (val value = decoder.decodeString().lowercase()) {
        "fragment" -> ResponseMode.Fragment
        "query" -> ResponseMode.Query
        else -> ResponseMode.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: ResponseMode) = encoder.encodeString(value.toString())
}


@Serializable(with = ResponseModeSerializer::class)
sealed interface ResponseMode {
    object Query : ResponseMode {
        override fun toString(): String = "query"
    }

    object Fragment : ResponseMode {
        override fun toString(): String = "fragment"
    }


    data class Unknown(private val value: String) : ResponseMode {
        override fun toString(): String = value
    }
}
