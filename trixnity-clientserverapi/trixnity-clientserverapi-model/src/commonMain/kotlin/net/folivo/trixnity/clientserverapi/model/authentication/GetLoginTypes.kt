package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/_matrix/client/v3/login")
@HttpMethod(GET)
@WithoutAuth
object GetLoginTypes : MatrixEndpoint<Unit, GetLoginTypes.Response> {
    @Serializable
    data class Response(
        @SerialName("flows")
        val flows: Set<LoginType>,
    )
}