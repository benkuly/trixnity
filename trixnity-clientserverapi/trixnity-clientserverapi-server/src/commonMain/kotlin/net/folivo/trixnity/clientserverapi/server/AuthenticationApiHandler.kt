package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA

interface AuthenticationApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3registeravailable">matrix spec</a>
     */
    suspend fun isUsernameAvailable(context: MatrixEndpointContext<IsUsernameAvailable, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3register">matrix spec</a>
     */
    suspend fun register(context: MatrixEndpointContext<Register, RequestWithUIA<Register.Request>, ResponseWithUIA<Register.Response>>): ResponseWithUIA<Register.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3login">matrix spec</a>
     */
    suspend fun getLoginTypes(context: MatrixEndpointContext<GetLoginTypes, Unit, GetLoginTypes.Response>): GetLoginTypes.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3login">matrix spec</a>
     */
    suspend fun login(context: MatrixEndpointContext<Login, Login.Request, Login.Response>): Login.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3logout">matrix spec</a>
     */
    suspend fun logout(context: MatrixEndpointContext<Logout, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3logoutall">matrix spec</a>
     */
    suspend fun logoutAll(context: MatrixEndpointContext<LogoutAll, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountdeactivate">matrix spec</a>
     */
    suspend fun deactivateAccount(context: MatrixEndpointContext<DeactivateAccount, RequestWithUIA<DeactivateAccount.Request>, ResponseWithUIA<DeactivateAccount.Response>>): ResponseWithUIA<DeactivateAccount.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountpassword">matrix spec</a>
     */
    suspend fun changePassword(context: MatrixEndpointContext<ChangePassword, RequestWithUIA<ChangePassword.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>
}