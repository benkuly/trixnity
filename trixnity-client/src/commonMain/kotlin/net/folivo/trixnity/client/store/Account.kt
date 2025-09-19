package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId

@Serializable
data class Account(
    val olmPickleKey: String,
    val baseUrl: String,
    val userId: UserId,
    val deviceId: String,
    val accessToken: String?,
    val refreshToken: String?,
    val syncBatchToken: String?,
    val filterId: String?,
    val backgroundFilterId: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val isLocked: Boolean = false,
    val oauth2ClientId: String? = null,
    val oauth2Login: Boolean = false
)
