package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ResponseModeSerializer::class)
sealed interface ResponseMode {
    val value: String

    object Query : ResponseMode {
        override val value: String = "query"
    }

    object Fragment : ResponseMode {
        override val value: String = "fragment"
    }

    data class Unknown(override val value: String) : ResponseMode
}

object ResponseModeSerializer : KSerializer<ResponseMode> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseMode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ResponseMode =
        when (val value = decoder.decodeString().lowercase()) {
            ResponseMode.Fragment.value -> ResponseMode.Fragment
            ResponseMode.Query.value -> ResponseMode.Query
            else -> ResponseMode.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: ResponseMode) =
        encoder.encodeString(value.value)
}