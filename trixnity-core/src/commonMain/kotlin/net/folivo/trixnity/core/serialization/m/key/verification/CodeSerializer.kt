package net.folivo.trixnity.core.serialization.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.*

object CodeSerializer : KSerializer<Code> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Code", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Code {
        return when (val value = decoder.decodeString()) {
            User.value -> User
            Timeout.value -> Timeout
            UnknownTransaction.value -> UnknownTransaction
            UnknownMethod.value -> UnknownMethod
            UnexpectedMessage.value -> UnexpectedMessage
            KeyMismatch.value -> KeyMismatch
            UserMismatch.value -> UserMismatch
            InvalidMessage.value -> InvalidMessage
            Accepted.value -> Accepted
            MismatchedCommitment.value -> MismatchedCommitment
            MismatchedSas.value -> MismatchedSas
            else -> Unknown(value)
        }
    }

    override fun serialize(encoder: Encoder, value: Code) {
        encoder.encodeString(value.value)
    }
}