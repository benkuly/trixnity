package net.folivo.trixnity.clientserverapi.client

import kotlinx.serialization.Serializable
import okio.ByteString.Companion.toByteString

@Serializable
data class BearerTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val oauth2Login: Boolean = false,
    val oauth2ClientId: String? = null
) {
    override fun toString(): String =
        "BearerTokens(" +
                "accessToken=${accessToken.passwordHash()}, " +
                "refreshToken=${refreshToken?.passwordHash()?.let { "hash:$it" }}, " +
                "oauth2Login=$oauth2Login, " +
                "oauth2ClientId=$oauth2ClientId" +
                ")"

    private fun String.passwordHash() = "[hash:" + encodeToByteArray().toByteString().sha256().hex().take(6) + "]"
}

interface BearerTokensStore {
    suspend fun getBearerTokens(): BearerTokens?
    suspend fun setBearerTokens(bearerTokens: BearerTokens)

    class InMemory(initialValue: BearerTokens? = null) : BearerTokensStore {
        var bearerTokens = initialValue

        override suspend fun getBearerTokens(): BearerTokens? = bearerTokens

        override suspend fun setBearerTokens(bearerTokens: BearerTokens) {
            this.bearerTokens = bearerTokens
        }
    }
}
