package net.folivo.trixnity.client.api.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.client.api.authentication.IdentifierType.*
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

@Serializable(with = IdentifierTypeSerializer::class)
sealed class IdentifierType {

    @Serializable
    data class User(
        @SerialName("user")
        val user: String
    ) : IdentifierType() {
        companion object {
            const val name: String = "m.id.user"
        }
    }

    @Serializable
    data class Thirdparty(
        @SerialName("medium")
        val medium: String,
        @SerialName("address")
        val address: String
    ) : IdentifierType() {
        companion object {
            const val name: String = "m.id.thirdparty"
        }
    }

    @Serializable
    data class Phone(
        @SerialName("country")
        val country: String,
        @SerialName("phone")
        val number: String
    ) : IdentifierType() {
        companion object {
            const val name: String = "m.id.phone"
        }
    }

    @Serializable
    data class Unknown(
        @SerialName("type")
        val name: String
    ) : IdentifierType()
}

object IdentifierTypeSerializer : KSerializer<IdentifierType> {
    override fun deserialize(decoder: Decoder): IdentifierType {
        throw SerializationException("should never be serialized")
    }

    override fun serialize(encoder: Encoder, value: IdentifierType) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            when (value) {
                is User -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(User.serializer(), "type" to User.name), value
                )
                is Thirdparty -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(Thirdparty.serializer(), "type" to Thirdparty.name), value
                )
                is Phone -> encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(Phone.serializer(), "type" to Phone.name), value
                )
                is Unknown -> throw SerializationException("unknown identifier type should never be serialized")
            }
        )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LoginType")
}