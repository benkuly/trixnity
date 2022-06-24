package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mignored_user_list">matrix spec</a>
 */
@Serializable
data class IgnoredUserListEventContent(
    @SerialName("ignored_users") val ignoredUsers: Map<UserId, JsonObject>,
) : GlobalAccountDataEventContent