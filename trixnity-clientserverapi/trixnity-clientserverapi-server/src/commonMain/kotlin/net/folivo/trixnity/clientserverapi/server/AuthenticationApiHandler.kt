package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA

interface AuthenticationApiHandler {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3accountwhoami">matrix spec</a>
     */
    suspend fun whoAmI(context: MatrixEndpointContext<WhoAmI, Unit, WhoAmI.Response>): WhoAmI.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
     */
    suspend fun getWellKnown(context: MatrixEndpointContext<GetWellKnown, Unit, DiscoveryInformation>): DiscoveryInformation

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv1registermloginregistration_tokenvalidity">matrix spec</a>
     */
    suspend fun isRegistrationTokenValid(context: MatrixEndpointContext<IsRegistrationTokenValid, Unit, IsRegistrationTokenValid.Response>): IsRegistrationTokenValid.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3registeravailable">matrix spec</a>
     */
    suspend fun isUsernameAvailable(context: MatrixEndpointContext<IsUsernameAvailable, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountpasswordemailrequesttoken">matrix spec</a>
     */
    suspend fun getEmailRequestTokenForPassword(context: MatrixEndpointContext<GetEmailRequestTokenForPassword, GetEmailRequestTokenForPassword.Request, GetEmailRequestTokenForPassword.Response>): GetEmailRequestTokenForPassword.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3registeremailrequesttoken">matrix spec</a>
     */
    suspend fun getEmailRequestTokenForRegistration(context: MatrixEndpointContext<GetEmailRequestTokenForRegistration, GetEmailRequestTokenForRegistration.Request, GetEmailRequestTokenForRegistration.Response>): GetEmailRequestTokenForRegistration.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3accountpasswordmsisdnrequesttoken">matrix spec</a>
     */
    suspend fun getMsisdnRequestTokenForPassword(context: MatrixEndpointContext<GetMsisdnRequestTokenForPassword, GetMsisdnRequestTokenForPassword.Request, GetMsisdnRequestTokenForPassword.Response>): GetMsisdnRequestTokenForPassword.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3registermsisdnrequesttoken">matrix spec</a>
     */
    suspend fun getMsisdnRequestTokenForRegistration(context: MatrixEndpointContext<GetMsisdnRequestTokenForRegistration, GetMsisdnRequestTokenForRegistration.Request, GetMsisdnRequestTokenForRegistration.Response>): GetMsisdnRequestTokenForRegistration.Response

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

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3account3pid">matrix spec</a>
     */
    suspend fun getThirdPartyIdentifiers(context: MatrixEndpointContext<GetThirdPartyIdentifiers, Unit, GetThirdPartyIdentifiers.Response>): GetThirdPartyIdentifiers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3account3pidadd">matrix spec</a>
     */
    suspend fun addThirdPartyIdentifiers(context: MatrixEndpointContext<AddThirdPartyIdentifiers, RequestWithUIA<AddThirdPartyIdentifiers.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3account3pidbind">matrix spec</a>
     */
    suspend fun bindThirdPartyIdentifiers(context: MatrixEndpointContext<BindThirdPartyIdentifiers, BindThirdPartyIdentifiers.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3account3piddelete">matrix spec</a>
     */
    suspend fun deleteThirdPartyIdentifiers(context: MatrixEndpointContext<DeleteThirdPartyIdentifiers, DeleteThirdPartyIdentifiers.Request, DeleteThirdPartyIdentifiers.Response>): DeleteThirdPartyIdentifiers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3account3pidunbind">matrix spec</a>
     */
    suspend fun unbindThirdPartyIdentifiers(context: MatrixEndpointContext<UnbindThirdPartyIdentifiers, UnbindThirdPartyIdentifiers.Request, UnbindThirdPartyIdentifiers.Response>): UnbindThirdPartyIdentifiers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3useruseridopenidrequest_token">matrix spec</a>
     */
    suspend fun getOIDCRequestToken(context: MatrixEndpointContext<GetOIDCRequestToken, Unit, GetOIDCRequestToken.Response>): GetOIDCRequestToken.Response
}