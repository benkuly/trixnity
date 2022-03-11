package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/account/deactivate")
data class DeactivateAccount(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<DeactivateAccount.Request, Unit>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("id_server")
        val identityServer: String?,
    )
}