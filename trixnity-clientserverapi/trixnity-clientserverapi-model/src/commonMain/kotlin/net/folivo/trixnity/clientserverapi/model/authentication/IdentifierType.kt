package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType.*

@Serializable(with = IdentifierTypeSerializer::class)
sealed class IdentifierType {
    abstract val name: String

    @Serializable
    data class User(
        @SerialName("user")
        val user: String
    ) : IdentifierType() {
        @SerialName("type")
        override val name = "m.id.user"
    }

    @Serializable
    data class Thirdparty(
        @SerialName("medium")
        val medium: String,
        @SerialName("address")
        val address: String
    ) : IdentifierType() {
        @SerialName("type")
        override val name = "m.id.thirdparty"
    }

    @Serializable
    data class Phone(
        @SerialName("country")
        val country: String,
        @SerialName("phone")
        val number: String
    ) : IdentifierType() {
        @SerialName("type")
        override val name = "m.id.phone"
    }

    data class Unknown(
        override val name: String,
        val raw: JsonElement
    ) : IdentifierType()
}

object IdentifierTypeSerializer : KSerializer<IdentifierType> {
    override fun deserialize(decoder: Decoder): IdentifierType {
        require(decoder is JsonDecoder)
        return try {
            val jsonObject = decoder.decodeJsonElement().jsonObject
            when (val name = jsonObject["type"]?.jsonPrimitive?.content) {
                "m.id.user" -> decoder.json.decodeFromJsonElement<User>(jsonObject)
                "m.id.thirdparty" -> decoder.json.decodeFromJsonElement<Thirdparty>(jsonObject)
                "m.id.phone" -> decoder.json.decodeFromJsonElement<Phone>(jsonObject)
                else -> Unknown(name ?: "", jsonObject)
            }
        } catch (exception: Exception) {
            Unknown("", decoder.decodeJsonElement())
        }
    }

    override fun serialize(encoder: Encoder, value: IdentifierType) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            when (value) {
                is User -> encoder.json.encodeToJsonElement(value)
                is Thirdparty -> encoder.json.encodeToJsonElement(value)
                is Phone -> encoder.json.encodeToJsonElement(value)
                is Unknown -> value.raw
            }
        )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LoginType")
}