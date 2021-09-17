package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedFile(
    @SerialName("url") val url: String,
    @SerialName("key") val key: JWK,
    @SerialName("iv") val initialisationVector: String,
    @SerialName("hashes") val hashes: Map<String, String>,
    @SerialName("v") val version: String = "v2"
) {
    @Serializable
    data class JWK(
        @SerialName("k") val key: String,
        @SerialName("key") val keyType: String = "oct",
        @SerialName("key_opts") val keyOperations: Set<String> = setOf("encrypt", "decrypt"),
        @SerialName("alg") val algorithm: String = "A256CTR",
        @SerialName("ext") val extractable: Boolean = true
    )
}