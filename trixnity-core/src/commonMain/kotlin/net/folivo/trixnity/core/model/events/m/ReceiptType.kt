package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReceiptTypeSerializer::class)
abstract class ReceiptType {
    abstract val name: String

    data object Read : ReceiptType() {
        override val name = "m.read"
    }

    data object PrivateRead : ReceiptType() {
        override val name = "m.read.private"
    }

    data object FullyRead : ReceiptType() {
        override val name = "m.fully_read"
    }

    data class Unknown(override val name: String) : ReceiptType()
}

object ReceiptTypeSerializer : KSerializer<ReceiptType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReceiptTypeSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ReceiptType {
        return when (val name = decoder.decodeString()) {
            ReceiptType.Read.name -> ReceiptType.Read
            ReceiptType.PrivateRead.name -> ReceiptType.PrivateRead
            ReceiptType.FullyRead.name -> ReceiptType.FullyRead
            else -> ReceiptType.Unknown(name)
        }
    }

    override fun serialize(encoder: Encoder, value: ReceiptType) {
        encoder.encodeString(value.name)
    }

}