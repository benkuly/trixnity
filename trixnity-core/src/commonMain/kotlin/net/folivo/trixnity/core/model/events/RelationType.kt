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

    data class UnknownRelationType(override val name: String) : RelationType
}

object RelationTypeSerializer : KSerializer<RelationType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RelationTypeSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): RelationType {
        return when (val name = decoder.decodeString()) {
            RelationType.Reference.name -> RelationType.Reference
            else -> RelationType.UnknownRelationType(name)
        }
    }

    override fun serialize(encoder: Encoder, value: RelationType) {
        encoder.encodeString(
            when (value) {
                RelationType.Reference -> RelationType.Reference.name
                is RelationType.UnknownRelationType -> value.name
            }
        )
    }
}