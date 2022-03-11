package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/register")
data class Register(
    @SerialName("kind") val kind: AccountType? = null
) : MatrixJsonEndpoint<Register.Request, Register.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("username") val username: String?,
        @SerialName("password") val password: String?,
        @SerialName("device_id") val deviceId: String?,
        @SerialName("initial_device_display_name") val initialDeviceDisplayName: String?,
        @SerialName("inhibit_login") val inhibitLogin: Boolean?,
        @SerialName("type") val type: String? = null
    )

    @Serializable
    data class Response(
        @SerialName("user_id") val userId: UserId,
        @SerialName("device_id") val deviceId: String? = null,
        @SerialName("access_token") val accessToken: String? = null
    )
}