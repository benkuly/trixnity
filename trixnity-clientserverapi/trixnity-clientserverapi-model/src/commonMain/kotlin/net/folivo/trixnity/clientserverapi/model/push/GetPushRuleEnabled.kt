package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRuleKind

@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}/enabled")
@HttpMethod(GET)
data class GetPushRuleEnabled(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetPushRuleEnabled.Response> {
    @Serializable
    data class Response(
        @SerialName("enabled")
        val enabled: Boolean,
    )
}