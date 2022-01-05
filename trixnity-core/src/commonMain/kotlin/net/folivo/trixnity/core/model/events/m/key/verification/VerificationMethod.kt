package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = VerificationMethodSerializer::class)
sealed class VerificationMethod {
    abstract val value: String

    object Sas : VerificationMethod() {
        override val value = "m.sas.v1"
    }

    data class Unknown(override val value: String) : VerificationMethod()
}

object VerificationMethodSerializer : KSerializer<VerificationMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VerificationMethodSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): VerificationMethod {
        return when (val value = decoder.decodeString()) {
            VerificationMethod.Sas.value -> VerificationMethod.Sas
            else -> VerificationMethod.Unknown(value)
        }
    }

    override fun serialize(encoder: Encoder, value: VerificationMethod) {
        encoder.encodeString(value.value)
    }
}