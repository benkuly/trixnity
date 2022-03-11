package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRule

@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
data class GetPushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: String,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, PushRule>() {
    @Transient
    override val method = Get
}