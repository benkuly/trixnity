package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mignored_user_list">matrix spec</a>
 */
@Serializable
data class IgnoredUserListEventContent(
    @SerialName("ignored_users") val ignoredUsers: Map<UserId, JsonObject>,
) : GlobalAccountDataEventContent