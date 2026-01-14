package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3pushrulesscopekindruleid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/pushrules/{scope}/{kind}/{ruleId}")
@HttpMethod(GET)
data class GetPushRule(
    @SerialName("scope") val scope: String,
    @SerialName("kind") val kind: PushRuleKind,
    @SerialName("ruleId") val ruleId: String,
) : MatrixEndpoint<Unit, PushRule> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: PushRule?
    ): KSerializer<PushRule> {
        val serializer = when (kind) {
            PushRuleKind.OVERRIDE -> PushRule.Override.serializer()
            PushRuleKind.CONTENT -> PushRule.Content.serializer()
            PushRuleKind.ROOM -> PushRule.Room.serializer()
            PushRuleKind.SENDER -> PushRule.Sender.serializer()
            PushRuleKind.UNDERRIDE -> PushRule.Underride.serializer()
        }
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<PushRule>
    }
}