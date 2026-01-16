package de.connect2x.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.push.PushAction
import de.connect2x.trixnity.core.model.push.PushRuleKind

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3pushrulesscopekindruleidactions">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}/actions")
@HttpMethod(PUT)
data class SetPushRuleActions(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
) : MatrixEndpoint<SetPushRuleActions.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("actions")
        val actions: Set<PushAction>,
    )
}