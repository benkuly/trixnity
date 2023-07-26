package net.folivo.trixnity.clientserverapi.model.authentication

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#get_matrixclientv1registermloginregistration_tokenvalidity">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/register/m.login.registration_token/validity")
@HttpMethod(GET)
@WithoutAuth
data class IsRegistrationTokenValid(
    @SerialName("token") val token: String,
) : MatrixEndpoint<Unit, IsRegistrationTokenValid.Response> {
    @Serializable
    data class Response(
        @SerialName("valid") val valid: Boolean
    )
}