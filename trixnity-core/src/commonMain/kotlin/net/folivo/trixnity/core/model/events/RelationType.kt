package net.folivo.trixnity.core.model.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RelationTypeSerializer::class)
sealed interface RelationType {
    val name: String

    object Reference : RelationType {
        override val name = "m.reference"
    }

    data class Unknown(override val name: String) : RelationType

    companion object {
        fun of(name: String) = when (name) {
            Reference.name -> Reference
            else -> Unknown(name)
        }
    }
}

object RelationTypeSerializer : KSerializer<RelationType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RelationTypeSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): RelationType {
        return RelationType.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: RelationType) {
        encoder.encodeString(value.name)
    }
}