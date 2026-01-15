package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.user.Profile
import net.folivo.trixnity.core.model.UserId

@Serializable
data class Account(
    val olmPickleKey: String?,
    @Deprecated("has been moved to Authentication, will be removed after a migration period")
    val baseUrl: String? = null,
    val userId: UserId,
    val deviceId: String,
    @Deprecated("has been moved to Authentication, will be removed after a migration period")
    val accessToken: String? = null,
    @Deprecated("has been moved to Authentication, will be removed after a migration period")
    val refreshToken: String? = null,
    val syncBatchToken: String?,
    val filterId: String?,
    val backgroundFilterId: String?,
    val profile: Profile? = null,
)
