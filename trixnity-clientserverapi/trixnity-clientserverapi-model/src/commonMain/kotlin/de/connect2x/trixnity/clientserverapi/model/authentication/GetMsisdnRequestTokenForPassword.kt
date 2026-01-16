package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3accountpasswordmsisdnrequesttoken">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/account/password/msisdn/requestToken")
@HttpMethod(POST)
@Auth(AuthRequired.NO)
object GetMsisdnRequestTokenForPassword :
    MatrixEndpoint<GetMsisdnRequestTokenForPassword.Request, GetMsisdnRequestTokenForPassword.Response> {
    @Serializable
    data class Request(
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("country") val country: String,
        @SerialName("id_access_token") val idAccessToken: String? = null,
        @SerialName("id_server") val idServer: String? = null,
        @SerialName("next_link") val nextLink: String? = null,
        @SerialName("phone_number") val phoneNumber: String,
        @SerialName("send_attempt") val sendAttempt: Long
    )

    @Serializable
    data class Response(
        @SerialName("sid") val sessionId: String,
        @SerialName("submit_url") val submitUrl: String? = null,
    )
}