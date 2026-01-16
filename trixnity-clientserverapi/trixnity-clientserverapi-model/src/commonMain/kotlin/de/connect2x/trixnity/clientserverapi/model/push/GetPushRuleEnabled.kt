package de.connect2x.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.push.PushRuleKind

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3pushrulesscopekindruleidenabled">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}/enabled")
@HttpMethod(GET)
data class GetPushRuleEnabled(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
) : MatrixEndpoint<Unit, GetPushRuleEnabled.Response> {
    @Serializable
    data class Response(
        @SerialName("enabled")
        val enabled: Boolean,
    )
}