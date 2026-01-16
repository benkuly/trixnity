package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RelationType.Serializer::class)
sealed interface RelationType {
    val name: String

    data object Reference : RelationType {
        override val name = "m.reference"
    }

    data object Replace : RelationType {
        override val name = "m.replace"
    }

    data object Thread : RelationType {
        override val name = "m.thread"
    }

    data object Annotation : RelationType {
        override val name: String = "m.annotation"
    }

    /**
     * This is an abstraction, it does not exist on this level in the matrix spec. Therefore, don't use it in Matrix Endpoints.
     */
    data object Reply : RelationType {
        override val name: String = "m.in_reply_to"
    }

    data class Unknown(override val name: String) : RelationType

    companion object {
        fun of(name: String) = when (name) {
            Reference.name -> Reference
            Replace.name -> Replace
            Reply.name -> Reply
            Thread.name -> Thread
            Annotation.name -> Annotation
            else -> Unknown(name)
        }
    }

    object Serializer : KSerializer<RelationType> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("RelationType", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): RelationType {
            return RelationType.of(decoder.decodeString())
        }

        override fun serialize(encoder: Encoder, value: RelationType) {
            encoder.encodeString(value.name)
        }
    }
}
