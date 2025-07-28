package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RoomIdSerializer::class)
data class RoomId(val full: String) {

    @Deprecated("RoomId should be considered as opaque String")
    constructor(localpart: String, domain: String) : this("${sigilCharacter}$localpart:$domain")

    companion object {
        const val sigilCharacter = '!'

        fun isValid(id: String): Boolean =
            id.length <= 255
                    && id.startsWith(sigilCharacter)
    }

    @Deprecated("RoomId should be considered as opaque String")
    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')

    @Deprecated("RoomId should be considered as opaque String")
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    val isValid by lazy { EventId.Companion.isValid(full) }

    override fun toString() = full
}

object RoomIdSerializer : KSerializer<RoomId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RoomId = RoomId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: RoomId) {
        encoder.encodeString(value.full)
    }
}