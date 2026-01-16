package de.connect2x.trixnity.clientserverapi.client.oauth2

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = TokenResponse.Serializer::class)
@KeepGeneratedSerializer
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("scope") val scope: Set<Scope>? = null,
) {
    object Serializer : JsonTransformingSerializer<TokenResponse>(TokenResponse.generatedSerializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement =
            JsonObject(buildMap {
                putAll(element.jsonObject)
                element.jsonObject["scope"]?.jsonPrimitive?.contentOrNull
                    ?.split(" ")
                    ?.map { JsonPrimitive(it) }
                    ?.let { put("scope", JsonArray(it)) }
            })

        override fun transformSerialize(element: JsonElement): JsonElement =
            JsonObject(buildMap {
                putAll(element.jsonObject)
                element.jsonObject["scope"]?.jsonArray
                    ?.joinToString(" ")
                    ?.let { put("scope", JsonPrimitive(it)) }
            })
    }
}