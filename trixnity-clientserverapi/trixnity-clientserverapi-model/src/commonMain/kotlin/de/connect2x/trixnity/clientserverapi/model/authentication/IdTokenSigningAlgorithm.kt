package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = IdTokenSigningAlgorithm.Serializer::class)
sealed interface IdTokenSigningAlgorithm {
    val value: String

    object RS256 : IdTokenSigningAlgorithm {
        override val value: String = "RS256"
    }

    data class Unknown(override val value: String) : IdTokenSigningAlgorithm

    object Serializer : KSerializer<IdTokenSigningAlgorithm> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("IdTokenSigningAlgorithm", SerialKind.ENUM)

        override fun deserialize(decoder: Decoder): IdTokenSigningAlgorithm =
            when (val value = decoder.decodeString()) {
                RS256.value -> RS256
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: IdTokenSigningAlgorithm) = encoder.encodeString(value.value)
    }
}
