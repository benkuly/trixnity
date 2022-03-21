package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/register")
@HttpMethod(POST)
@WithoutAuth
data class Register(
    @SerialName("kind") val kind: AccountType? = null,
) : MatrixUIAEndpoint<Register.Request, Register.Response> {
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