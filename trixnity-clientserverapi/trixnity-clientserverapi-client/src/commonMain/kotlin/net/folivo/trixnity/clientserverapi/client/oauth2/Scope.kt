package net.folivo.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = ScopeSerializer::class)
sealed interface Scope {
    val value: String

    object MatrixClientApi : Scope {
        override val value: String = "urn:matrix:client:api:*"
    }

    data class MatrixClientDevice(val deviceId: String) : Scope {
        companion object {
            const val PREFIX = "urn:matrix:client:device:"
        }

        override val value: String = "$PREFIX$deviceId"
    }

    data class Unknown(override val value: String) : Scope
}

object ScopeSerializer : KSerializer<Scope> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Scope", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Scope {
        val value = decoder.decodeString().lowercase()
        return when {
            value == Scope.MatrixClientApi.value -> Scope.MatrixClientApi
            value.startsWith(Scope.MatrixClientDevice.PREFIX) -> Scope.MatrixClientDevice(value.removePrefix(Scope.MatrixClientDevice.PREFIX))
            else -> Scope.Unknown(value)
        }
    }

    override fun serialize(encoder: Encoder, value: Scope) = encoder.encodeString(value.value)
}