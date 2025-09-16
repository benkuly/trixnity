package net.folivo.trixnity.crypto.jose

import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

data class Claims<T>(
    @SerialName("sub") val subject: String,
    @SerialName("iat") val issuedAt: Long?,
    @SerialName("iss") val issuer: Url?,
    val additional: T
) {
    // @OptIn(ExperimentalSerializationApi::class)
    // fun serialize(json: Json = Json): JsonObject {
    //     val additionalClaims = json.encodeToJsonElement(serializer(additional!!::class, listOf(), false), additional)
    //     return buildJsonObject {
    //         put("iss", JsonPrimitive(issuer.toString()))
    //         put("sub", JsonPrimitive(subject))
    //         put("iat", JsonPrimitive(issuedAt))
    //         if (additionalClaims is JsonObject) {
    //             additionalClaims.forEach { (k, v) -> put(k, v) }
    //         }
    //     }
    // }
}

inline fun <reified T> Claims<T>.serialize(json: Json = Json): JsonObject {
    val additionalClaims = json.encodeToJsonElement(serializer(), additional)
    return buildJsonObject {
        put("iss", JsonPrimitive(issuer.toString()))
        put("sub", JsonPrimitive(subject))
        put("iat", JsonPrimitive(issuedAt))
        if (additionalClaims is JsonObject) {
            additionalClaims.forEach { (k, v) -> put(k, v) }
        }
    }
}
