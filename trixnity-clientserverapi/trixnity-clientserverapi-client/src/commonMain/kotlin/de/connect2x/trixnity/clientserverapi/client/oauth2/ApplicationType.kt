package de.connect2x.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ApplicationType.Serializer::class)
sealed interface ApplicationType {
    val value: String

    object Web : ApplicationType {
        override val value: String = "web"
    }

    object Native : ApplicationType {
        override val value: String = "native"
    }

    data class Unknown(override val value: String) : ApplicationType

    object Serializer : KSerializer<ApplicationType> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ApplicationType", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ApplicationType {
            return when (decoder.decodeString()) {
                Web.value -> Web
                Native.value -> Native
                else -> Unknown(decoder.decodeString())
            }
        }

        override fun serialize(encoder: Encoder, value: ApplicationType) {
            encoder.encodeString(value.value)
        }
    }
}