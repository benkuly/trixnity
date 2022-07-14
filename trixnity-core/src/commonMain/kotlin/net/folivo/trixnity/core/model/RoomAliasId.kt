package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@Serializable(with = RoomAliasIdSerializer::class)
@JvmInline
value class RoomAliasId(val full: String) {

    constructor(localpart: String, domain: String) : this("${sigilCharacter}$localpart:$domain")

    companion object {
        const val sigilCharacter = '#'
    }

    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    override fun toString() = full
}

object RoomAliasIdSerializer : KSerializer<RoomAliasId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomAliasIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RoomAliasId = RoomAliasId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: RoomAliasId) {
        encoder.encodeString(value.full)
    }
}