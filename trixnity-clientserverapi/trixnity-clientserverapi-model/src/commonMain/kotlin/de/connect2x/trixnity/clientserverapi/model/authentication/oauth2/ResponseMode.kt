package de.connect2x.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ResponseMode.Serializer::class)
sealed interface ResponseMode {
    val value: String

    object Query : ResponseMode {
        override val value: String = "query"
    }

    object Fragment : ResponseMode {
        override val value: String = "fragment"
    }

    data class Unknown(override val value: String) : ResponseMode

    object Serializer : KSerializer<ResponseMode> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("ResponseMode", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ResponseMode =
            when (val value = decoder.decodeString().lowercase()) {
                Fragment.value -> Fragment
                Query.value -> Query
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: ResponseMode) =
            encoder.encodeString(value.value)
    }
}