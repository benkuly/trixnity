package net.folivo.trixnity.serverserverapi.model.discovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerKeys(
    @SerialName("server_name")
    val serverName: String,
    @SerialName("valid_until_ts")
    val validUntil: Long,
    @SerialName("old_verify_keys")
    val oldVerifyKeys: Map<String, OldVerifyKey>? = null,
    @SerialName("verify_keys")
    val verifyKeys: Map<String, VerifyKey>,
) {
    @Serializable
    data class OldVerifyKey(
        @SerialName("key")
        val keyValue: String,
        @SerialName("expired_ts")
        val expiredAt: Long,
    )

    @Serializable
    data class VerifyKey(
        @SerialName("key")
        val keyValue: String,
    )
}