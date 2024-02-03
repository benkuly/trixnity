package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SasMethodSerializer::class)
sealed interface SasMethod {
    val name: String

    data object Decimal : SasMethod {
        override val name: String = "decimal"
    }

    data object Emoji : SasMethod {
        override val name: String = "emoji"
    }

    data class Unknown(override val name: String) : SasMethod
}

class SasMethodSerializer : KSerializer<SasMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SasMethod", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SasMethod =
        when (val name = decoder.decodeString()) {
            SasMethod.Decimal.name -> SasMethod.Decimal
            SasMethod.Emoji.name -> SasMethod.Emoji
            else -> SasMethod.Unknown(name)
        }

    override fun serialize(encoder: Encoder, value: SasMethod) =
        encoder.encodeString(value.name)
}