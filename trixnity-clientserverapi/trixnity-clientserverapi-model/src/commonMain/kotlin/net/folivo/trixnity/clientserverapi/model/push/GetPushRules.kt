package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRuleSet

@Serializable
@Resource("/_matrix/client/v3/pushrules/")
data class GetPushRules(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Unit, GetPushRules.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("global") val global: PushRuleSet,
    )
}