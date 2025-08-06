package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.util.MatrixIdRegex

@Serializable(with = UserIdSerializer::class)
data class UserId(val full: String) {

    constructor(localpart: String, domain: String) : this("$sigilCharacter$localpart:$domain")

    companion object {
        const val sigilCharacter = '@'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.userId)
        fun isReasonable(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.reasonableUserId)
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    val isValid by lazy { isValid(full) }
    val isReasonable by lazy { isReasonable(full) }

    override fun toString() = full
}

object UserIdSerializer : KSerializer<UserId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UserIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UserId = UserId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: UserId) {
        encoder.encodeString(value.full)
    }
}
