package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAlgorithm
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData

@Serializable(with = SetRoomKeysVersionRequestSerializer::class)
sealed class SetRoomKeysVersionRequest {
    abstract val algorithm: RoomKeyBackupAlgorithm
    abstract val version: String?

    @Serializable
    data class V1(
        @SerialName("auth_data")
        val authData: RoomKeyBackupAuthData.RoomKeyBackupV1AuthData,
        @SerialName("version")
        override val version: String? = null,
        @SerialName("algorithm")
        override val algorithm: RoomKeyBackupAlgorithm.RoomKeyBackupV1 = RoomKeyBackupAlgorithm.RoomKeyBackupV1,
    ) : SetRoomKeysVersionRequest()
}

object SetRoomKeysVersionRequestSerializer : KSerializer<SetRoomKeysVersionRequest> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SetRoomKeysVersionRequestSerializer")

    override fun deserialize(decoder: Decoder): SetRoomKeysVersionRequest {
        throw SerializationException("deserialize not allowed")
    }

    override fun serialize(encoder: Encoder, value: SetRoomKeysVersionRequest) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is SetRoomKeysVersionRequest.V1 -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}
