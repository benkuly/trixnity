package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition

@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
data class SetPushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: String,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("before") val beforeRuleId: String? = null,
    @SerialName("after") val afterRuleId: String? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetPushRule.Request, Unit>() {
    @Transient
    override val method = Put

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