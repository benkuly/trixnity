package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/logout/all")
@HttpMethod(POST)
data class LogoutAll(
    @SerialName("user_id") val asUserId: UserId? = null,
) : MatrixEndpoint<Unit, Unit>