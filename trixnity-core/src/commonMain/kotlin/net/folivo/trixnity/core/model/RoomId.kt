package net.folivo.trixnity.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@Serializable(with = RoomIdSerializer::class)
data class RoomId(val full: String) {

    constructor(localpart: String, domain: String) : this("${sigilCharacter}$localpart:$domain")

    companion object {
        const val sigilCharacter = '!'
    }


    val localpart: String
        get() = full.trimStart(sigilCharacter).substringBefore(':')
    val domain: String
        get() = full.trimStart(sigilCharacter).substringAfter(':')

    override fun toString() = full
}

object RoomIdSerializer : KSerializer<RoomId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RoomIdSerializer", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): RoomId = RoomId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: RoomId) {
        encoder.encodeString(value.full)
    }
}