package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAlgorithm
import net.folivo.trixnity.core.model.keys.RoomKeyBackupAuthData

@Serializable(with = SetRoomKeysVersionRequestSerializer::class)
sealed interface SetRoomKeyBackupVersionRequest {
    val algorithm: RoomKeyBackupAlgorithm
    val version: String?

    @Serializable
    data class V1(
        @SerialName("auth_data")
        val authData: RoomKeyBackupAuthData.RoomKeyBackupV1AuthData,
        @SerialName("version")
        override val version: String? = null,
        @SerialName("algorithm")
        override val algorithm: RoomKeyBackupAlgorithm.RoomKeyBackupV1 = RoomKeyBackupAlgorithm.RoomKeyBackupV1,
    ) : SetRoomKeyBackupVersionRequest

    data class Unknown(
        override val algorithm: RoomKeyBackupAlgorithm,
        val raw: JsonObject
    ) : SetRoomKeyBackupVersionRequest {
        override val version: String? = null
    }
}

object SetRoomKeysVersionRequestSerializer : KSerializer<SetRoomKeyBackupVersionRequest> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SetRoomKeysVersionRequestSerializer")

    override fun deserialize(decoder: Decoder): SetRoomKeyBackupVersionRequest {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("expected json object")
        return when (jsonObject["algorithm"]?.jsonPrimitive?.content) {
            RoomKeyBackupAlgorithm.RoomKeyBackupV1.name ->
                decoder.json.decodeFromJsonElement<SetRoomKeyBackupVersionRequest.V1>(jsonObject)

            else -> SetRoomKeyBackupVersionRequest.Unknown(RoomKeyBackupAlgorithm.Unknown(""), jsonObject)
        }
    }

    override fun serialize(encoder: Encoder, value: SetRoomKeyBackupVersionRequest) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is SetRoomKeyBackupVersionRequest.V1 -> encoder.json.encodeToJsonElement(value)
            is SetRoomKeyBackupVersionRequest.Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}
