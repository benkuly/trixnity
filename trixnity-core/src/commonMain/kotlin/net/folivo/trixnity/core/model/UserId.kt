package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = UserIdSerializer::class)
data class UserId(val full: String): Mention {

    constructor(localpart: String, domain: String) : this("$sigilCharacter$localpart:$domain")

    companion object {
        const val sigilCharacter = '@'
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    override fun toString() = full
}

object UserIdSerializer : KSerializer<UserId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UserIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UserId = UserId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: UserId) {
        encoder.encodeString(value.full)
    }
}
