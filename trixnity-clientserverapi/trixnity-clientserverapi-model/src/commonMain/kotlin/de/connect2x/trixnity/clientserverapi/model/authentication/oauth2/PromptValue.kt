package de.connect2x.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PromptValue.Serializer::class)
sealed interface PromptValue {
    val value: String

    object Create : PromptValue {
        override val value: String = "create"
    }

    data class Unknown(override val value: String) : PromptValue

    object Serializer : KSerializer<PromptValue> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("PromptValue", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PromptValue =
            when (val value = decoder.decodeString().lowercase()) {
                Create.value -> Create
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: PromptValue) =
            encoder.encodeString(value.value)
    }
}