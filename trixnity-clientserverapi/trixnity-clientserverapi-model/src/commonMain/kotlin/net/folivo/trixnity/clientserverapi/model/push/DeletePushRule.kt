package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.DELETE
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRuleKind

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
@HttpMethod(DELETE)
data class DeletePushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Unit>