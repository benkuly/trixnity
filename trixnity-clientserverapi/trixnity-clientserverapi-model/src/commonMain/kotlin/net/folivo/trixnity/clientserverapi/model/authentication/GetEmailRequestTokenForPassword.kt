package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/_matrix/client/v3/account/password/email/requestToken")
@HttpMethod(POST)
@WithoutAuth
object GetEmailRequestTokenForPassword :
    MatrixEndpoint<GetEmailRequestTokenForPassword.Request, GetEmailRequestTokenForPassword.Response> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("email") val email: String,
        @SerialName("id_access_token") val idAccessToken: String? = null,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("next_link") val nextLink: String? = null,
        @SerialName("send_attempt") val sendAttempt: Long
    )

    @Serializable
    data class Response(
        @SerialName("sid") val sessionId: String,
        @SerialName("submit_url") val submitUrl: String? = null,
    )
}