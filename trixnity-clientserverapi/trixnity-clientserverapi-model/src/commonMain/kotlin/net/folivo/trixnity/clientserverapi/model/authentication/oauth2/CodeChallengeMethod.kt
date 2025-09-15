package net.folivo.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CodeChallengeMethodSerializer : KSerializer<CodeChallengeMethod> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("CodeChallengeMethod", SerialKind.ENUM)

    override fun deserialize(decoder: Decoder): CodeChallengeMethod =
        when (val value = decoder.decodeString().lowercase()) {
            "s256" -> CodeChallengeMethod.S256
            else -> CodeChallengeMethod.Unknown(value)
        }

    override fun serialize(encoder: Encoder, value: CodeChallengeMethod) = encoder.encodeString(value.toString())
}


@Serializable(with = CodeChallengeMethodSerializer::class)
sealed interface CodeChallengeMethod {
    object S256 : CodeChallengeMethod {
        override fun toString(): String = "s256"
    }

    data class Unknown(private val value: String) : CodeChallengeMethod {
        override fun toString(): String = value
    }
}
