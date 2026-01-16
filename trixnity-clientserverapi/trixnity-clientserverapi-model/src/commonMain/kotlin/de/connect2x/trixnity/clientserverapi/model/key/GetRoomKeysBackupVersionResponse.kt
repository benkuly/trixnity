package de.connect2x.trixnity.clientserverapi.model.key

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAlgorithm
import de.connect2x.trixnity.core.model.keys.RoomKeyBackupAuthData

@Serializable(with = GetRoomKeysBackupVersionResponse.Serializer::class)
sealed interface GetRoomKeysBackupVersionResponse {
    val algorithm: RoomKeyBackupAlgorithm

    @Serializable
    data class V1(
        @SerialName("auth_data")
        val authData: RoomKeyBackupAuthData.RoomKeyBackupV1AuthData,
        @SerialName("count")
        val count: Long,
        @SerialName("etag")
        val etag: String,
        @SerialName("version")
        val version: String,
        @SerialName("algorithm")
        override val algorithm: RoomKeyBackupAlgorithm.RoomKeyBackupV1 = RoomKeyBackupAlgorithm.RoomKeyBackupV1,
    ) : GetRoomKeysBackupVersionResponse

    data class Unknown(val raw: JsonObject, override val algorithm: RoomKeyBackupAlgorithm) :
        GetRoomKeysBackupVersionResponse

    object Serializer : KSerializer<GetRoomKeysBackupVersionResponse> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GetRoomKeysVersionResponse")

        override fun deserialize(decoder: Decoder): GetRoomKeysBackupVersionResponse {
            require(decoder is JsonDecoder)
            val jsonObj = decoder.decodeJsonElement().jsonObject
            return when (val algorithm = decoder.json.decodeFromJsonElement<RoomKeyBackupAlgorithm>(
                jsonObj["algorithm"] ?: JsonPrimitive("unknown")
            )) {
                RoomKeyBackupAlgorithm.RoomKeyBackupV1 ->
                    decoder.json.decodeFromJsonElement<V1>(jsonObj)

                is RoomKeyBackupAlgorithm.Unknown -> Unknown(jsonObj, algorithm)
            }
        }

        override fun serialize(encoder: Encoder, value: GetRoomKeysBackupVersionResponse) {
            require(encoder is JsonEncoder)
            val jsonObject =
                when (value) {
                    is V1 -> encoder.json.encodeToJsonElement(value)
                    is Unknown -> value.raw
                }
            encoder.encodeJsonElement(jsonObject)
        }
    }
}