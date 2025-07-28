package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.util.MatrixIdRegex

@Serializable(with = RoomAliasIdSerializer::class)
data class RoomAliasId(val full: String) {

    constructor(localpart: String, domain: String) : this("${sigilCharacter}$localpart:$domain")

    companion object {
        const val sigilCharacter = '#'

        fun isValid(id: String): Boolean = id.length <= 255 && id.matches(MatrixIdRegex.roomAliasIdRegex)
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    val isValid by lazy { isValid(full) }

    override fun toString() = full
}

object RoomAliasIdSerializer : KSerializer<RoomAliasId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomAliasIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RoomAliasId = RoomAliasId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: RoomAliasId) {
        encoder.encodeString(value.full)
    }
}