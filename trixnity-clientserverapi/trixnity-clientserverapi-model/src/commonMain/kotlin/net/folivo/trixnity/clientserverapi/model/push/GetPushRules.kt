package net.folivo.trixnity.clientserverapi.model.push

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.push.PushRules

@Serializable
@Resource("/_matrix/client/v3/pushrules/")
@HttpMethod(GET)
data class GetPushRules(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, GetPushRules.Response> {
    @Serializable
    data class Response(
        @SerialName("global") val global: PushRules,
    )
}