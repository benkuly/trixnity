package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/account/deactivate")
@HttpMethod(POST)
data class DeactivateAccount(
    @SerialName("user_id") val asUserId: UserId? = null,
) : MatrixUIAEndpoint<DeactivateAccount.Request, DeactivateAccount.Response> {
    @Serializable
    data class Request(
        @SerialName("id_server")
        val identityServer: String?,
    )

    @Serializable
    data class Response(
        @SerialName("id_server_unbind_result")
        val idServerUnbindResult: IdServerUnbindResult,
    )
}