package de.connect2x.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VerificationMethod.Serializer::class)
sealed interface VerificationMethod {
    val value: String

    data object Sas : VerificationMethod {
        override val value = "m.sas.v1"
    }

    data class Unknown(override val value: String) : VerificationMethod

    object Serializer : KSerializer<VerificationMethod> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("VerificationMethod", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): VerificationMethod {
            return when (val value = decoder.decodeString()) {
                Sas.value -> Sas
                else -> Unknown(value)
            }
        }

        override fun serialize(encoder: Encoder, value: VerificationMethod) {
            encoder.encodeString(value.value)
        }
    }
}