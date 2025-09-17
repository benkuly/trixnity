package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object IdTokenSigningAlgorithmSerializer : KSerializer<IdTokenSigningAlgorithm> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("CodeChallengeMethod", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): IdTokenSigningAlgorithm =
        when (val value = decoder.decodeString()) {
            "RS256" -> IdTokenSigningAlgorithm.RS256
            else -> IdTokenSigningAlgorithm.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: IdTokenSigningAlgorithm) = encoder.encodeString(value.toString())
}


@Serializable(with = IdTokenSigningAlgorithmSerializer::class)
sealed interface IdTokenSigningAlgorithm {
    object RS256 : IdTokenSigningAlgorithm {
        override fun toString(): String = "RS256"
    }

    data class Unknown(private val value: String) : IdTokenSigningAlgorithm {
        override fun toString(): String = value
    }
}
