package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private class PromptValueSerializer : KSerializer<PromptValue> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("PromptValue", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): PromptValue = when (val value = decoder.decodeString().lowercase()) {
        "none" -> PromptValue.None
        "login" -> PromptValue.Login
        "consent" -> PromptValue.Consent
        "select_account" -> PromptValue.SelectAccount
        else -> PromptValue.Unknown(value)
    }

    override fun serialize(encoder: Encoder, value: PromptValue) = encoder.encodeString(value.toString())
}


@Serializable(with = PromptValueSerializer::class)
sealed interface PromptValue {
    object None : PromptValue {
        override fun toString(): String = "none"
    }

    object Login : PromptValue {
        override fun toString(): String = "login"
    }

    object Consent : PromptValue {
        override fun toString(): String = "consent"
    }


    object SelectAccount : PromptValue {
        override fun toString(): String = "select_account"
    }

    data class Unknown(private val value: String) : PromptValue {
        override fun toString(): String = value
    }
}
