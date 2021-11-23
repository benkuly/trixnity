package net.folivo.trixnity.core.serialization.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Unknown

object VerificationMethodSerializer : KSerializer<VerificationMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VerificationMethodSerializer", PrimitiveKind.STRING)

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