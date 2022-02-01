package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = UserIdSerializer::class)
class UserId : MatrixId {
    constructor(full: String) : super(full, '@')
    constructor(localpart: String, domain: String) : super(localpart, domain, '@')
}

object UserIdSerializer : KSerializer<UserId> {
    override fun deserialize(decoder: Decoder): UserId {
        return UserId(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UserId) {
        encoder.encodeString(value.full)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UserIdSerializer", PrimitiveKind.STRING)
}