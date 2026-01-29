package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.clientserverapi.model.user.Profile
import de.connect2x.trixnity.core.model.UserId
import kotlinx.serialization.Serializable

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
    val filter: Filter? = null,
    val profile: Profile? = null,
) {
    @Serializable
    data class Filter(
        val syncFilterId: String,
        val syncOnceFilterId: String,
        val eventTypesHash: String,
    )
}
