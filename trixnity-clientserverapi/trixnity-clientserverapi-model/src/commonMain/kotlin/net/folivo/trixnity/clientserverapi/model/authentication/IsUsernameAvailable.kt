package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/_matrix/client/v3/register/available")
@HttpMethod(GET)
@WithoutAuth
data class IsUsernameAvailable(
    @SerialName("username") val username: String,
) : MatrixEndpoint<Unit, Unit>