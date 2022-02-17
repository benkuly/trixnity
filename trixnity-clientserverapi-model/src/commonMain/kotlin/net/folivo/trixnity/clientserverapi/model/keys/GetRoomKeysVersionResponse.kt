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

@Serializable(with = GetRoomKeysVersionResponseSerializer::class)
sealed class GetRoomKeysVersionResponse {
    abstract val algorithm: RoomKeyBackupAlgorithm

    @Serializable
    data class V1(
        @SerialName("auth_data")
        val authData: RoomKeyBackupAuthData.RoomKeyBackupV1AuthData,
        @SerialName("count")
        val count: Int,
        @SerialName("etag")
        val etag: String,
        @SerialName("version")
        val version: String,
        @SerialName("algorithm")
        override val algorithm: RoomKeyBackupAlgorithm.RoomKeyBackupV1 = RoomKeyBackupAlgorithm.RoomKeyBackupV1,
    ) : GetRoomKeysVersionResponse()

    data class Unknown(val raw: JsonObject, override val algorithm: RoomKeyBackupAlgorithm) :
        GetRoomKeysVersionResponse()
}

object GetRoomKeysVersionResponseSerializer : KSerializer<GetRoomKeysVersionResponse> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GetRoomKeysVersionResponseSerializer")

    override fun deserialize(decoder: Decoder): GetRoomKeysVersionResponse {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val algorithm = decoder.json.decodeFromJsonElement<RoomKeyBackupAlgorithm>(
            jsonObj["algorithm"] ?: JsonPrimitive("unknown")
        )) {
            RoomKeyBackupAlgorithm.RoomKeyBackupV1 ->
                decoder.json.decodeFromJsonElement<GetRoomKeysVersionResponse.V1>(jsonObj)
            is RoomKeyBackupAlgorithm.Unknown -> GetRoomKeysVersionResponse.Unknown(jsonObj, algorithm)
        }
    }

    override fun serialize(encoder: Encoder, value: GetRoomKeysVersionResponse) {
        throw SerializationException("serialize not allowed")
    }
}