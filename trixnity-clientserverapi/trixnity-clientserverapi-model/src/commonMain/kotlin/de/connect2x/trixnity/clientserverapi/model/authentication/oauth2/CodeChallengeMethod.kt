package de.connect2x.trixnity.clientserverapi.model.authentication.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = CodeChallengeMethod.Serializer::class)
sealed class CodeChallengeMethod(val value: String) {
    object S256 : CodeChallengeMethod("S256")
    class Unknown(value: String) : CodeChallengeMethod(value)

    object Serializer : KSerializer<CodeChallengeMethod> {
        @OptIn(InternalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildSerialDescriptor("CodeChallengeMethod", SerialKind.ENUM)

        override fun deserialize(decoder: Decoder): CodeChallengeMethod =
            when (val value = decoder.decodeString()) {
                S256.value -> S256
                else -> Unknown(value)
            }

        override fun serialize(encoder: Encoder, value: CodeChallengeMethod) = encoder.encodeString(value.value)
    }
}