package de.connect2x.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv1registermloginregistration_tokenvalidity">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/register/m.login.registration_token/validity")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
data class IsRegistrationTokenValid(
    @SerialName("token") val token: String,
) : MatrixEndpoint<Unit, IsRegistrationTokenValid.Response> {
    @Serializable
    data class Response(
        @SerialName("valid") val valid: Boolean
    )
}