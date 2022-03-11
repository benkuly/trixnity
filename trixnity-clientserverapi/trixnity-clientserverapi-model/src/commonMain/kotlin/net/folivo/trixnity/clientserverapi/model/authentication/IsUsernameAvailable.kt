package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint

@Serializable
@Resource("/_matrix/client/v3/register/available")
data class IsUsernameAvailable(
    @SerialName("username") val username: String
) : MatrixJsonEndpoint<Unit, Unit>() {
    @Transient
    override val method = Get
}