package net.folivo.trixnity.core.model.events.m

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

    object Replace : RelationType {
        override val name = "m.replace"
    }

    object Thread : RelationType {
        override val name = "m.thread"
    }

    /**
     * This is an abstraction, it does not exist on this level in the matrix spec. Therefore, don't use it in Matrix Endpoints.
     */
    object Reply : RelationType {
        override val name: String = "m.in_reply_to"
    }

    data class Unknown(override val name: String) : RelationType

    companion object {
        fun of(name: String) = when (name) {
            Reference.name -> Reference
            Replace.name -> Replace
            Reply.name -> Reply
            Thread.name -> Thread
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