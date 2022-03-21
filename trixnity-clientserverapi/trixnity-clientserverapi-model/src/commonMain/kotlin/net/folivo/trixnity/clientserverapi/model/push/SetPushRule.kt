package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition

@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
@HttpMethod(PUT)
data class SetPushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: String,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("before") val beforeRuleId: String? = null,
    @SerialName("after") val afterRuleId: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetPushRule.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("actions")
        val actions: Set<PushAction>,
        @SerialName("conditions")
        val conditions: Set<PushCondition> = setOf(),
        @SerialName("pattern")
        val pattern: String? = null,
    )
}