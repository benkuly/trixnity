package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA

interface AuthenticationApiHandler {

    /**
     * @see [WhoAmI]
     */
    suspend fun whoAmI(context: MatrixEndpointContext<WhoAmI, Unit, WhoAmI.Response>): WhoAmI.Response

    /**
     * @see [IsRegistrationTokenValid]
     */
    suspend fun isRegistrationTokenValid(context: MatrixEndpointContext<IsRegistrationTokenValid, Unit, IsRegistrationTokenValid.Response>): IsRegistrationTokenValid.Response

    /**
     * @see [IsUsernameAvailable]
     */
    suspend fun isUsernameAvailable(context: MatrixEndpointContext<IsUsernameAvailable, Unit, Unit>)

    /**
     * @see [GetEmailRequestTokenForPassword]
     */
    suspend fun getEmailRequestTokenForPassword(context: MatrixEndpointContext<GetEmailRequestTokenForPassword, GetEmailRequestTokenForPassword.Request, GetEmailRequestTokenForPassword.Response>): GetEmailRequestTokenForPassword.Response

    /**
     * @see [GetEmailRequestTokenForRegistration]
     */
    suspend fun getEmailRequestTokenForRegistration(context: MatrixEndpointContext<GetEmailRequestTokenForRegistration, GetEmailRequestTokenForRegistration.Request, GetEmailRequestTokenForRegistration.Response>): GetEmailRequestTokenForRegistration.Response

    /**
     * @see [GetMsisdnRequestTokenForPassword]
     */
    suspend fun getMsisdnRequestTokenForPassword(context: MatrixEndpointContext<GetMsisdnRequestTokenForPassword, GetMsisdnRequestTokenForPassword.Request, GetMsisdnRequestTokenForPassword.Response>): GetMsisdnRequestTokenForPassword.Response

    /**
     * @see [GetMsisdnRequestTokenForRegistration]
     */
    suspend fun getMsisdnRequestTokenForRegistration(context: MatrixEndpointContext<GetMsisdnRequestTokenForRegistration, GetMsisdnRequestTokenForRegistration.Request, GetMsisdnRequestTokenForRegistration.Response>): GetMsisdnRequestTokenForRegistration.Response

    /**
     * @see [Register]
     */
    suspend fun register(context: MatrixEndpointContext<Register, RequestWithUIA<Register.Request>, ResponseWithUIA<Register.Response>>): ResponseWithUIA<Register.Response>

    /**
     * @see [SSORedirectTo]
     */
    suspend fun ssoRedirect(context: MatrixEndpointContext<SSORedirect, Unit, Unit>): String

    /**
     * @see [SSORedirectTo]
     */
    suspend fun ssoRedirectTo(context: MatrixEndpointContext<SSORedirectTo, Unit, Unit>): String

    /**
     * @see [GetLoginTypes]
     */
    suspend fun getLoginTypes(context: MatrixEndpointContext<GetLoginTypes, Unit, GetLoginTypes.Response>): GetLoginTypes.Response

    /**
     * @see [Login]
     */
    suspend fun login(context: MatrixEndpointContext<Login, Login.Request, Login.Response>): Login.Response

    /**
     * @see [Logout]
     */
    suspend fun logout(context: MatrixEndpointContext<Logout, Unit, Unit>)

    /**
     * @see [LogoutAll]
     */
    suspend fun logoutAll(context: MatrixEndpointContext<LogoutAll, Unit, Unit>)

    /**
     * @see [DeactivateAccount]
     */
    suspend fun deactivateAccount(context: MatrixEndpointContext<DeactivateAccount, RequestWithUIA<DeactivateAccount.Request>, ResponseWithUIA<DeactivateAccount.Response>>): ResponseWithUIA<DeactivateAccount.Response>

    /**
     * @see [ChangePassword]
     */
    suspend fun changePassword(context: MatrixEndpointContext<ChangePassword, RequestWithUIA<ChangePassword.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see [GetThirdPartyIdentifiers]
     */
    suspend fun getThirdPartyIdentifiers(context: MatrixEndpointContext<GetThirdPartyIdentifiers, Unit, GetThirdPartyIdentifiers.Response>): GetThirdPartyIdentifiers.Response

    /**
     * @see [AddThirdPartyIdentifiers]
     */
    suspend fun addThirdPartyIdentifiers(context: MatrixEndpointContext<AddThirdPartyIdentifiers, RequestWithUIA<AddThirdPartyIdentifiers.Request>, ResponseWithUIA<Unit>>): ResponseWithUIA<Unit>

    /**
     * @see [BindThirdPartyIdentifiers]
     */
    suspend fun bindThirdPartyIdentifiers(context: MatrixEndpointContext<BindThirdPartyIdentifiers, BindThirdPartyIdentifiers.Request, Unit>)

    /**
     * @see [DeleteThirdPartyIdentifiers]
     */
    suspend fun deleteThirdPartyIdentifiers(context: MatrixEndpointContext<DeleteThirdPartyIdentifiers, DeleteThirdPartyIdentifiers.Request, DeleteThirdPartyIdentifiers.Response>): DeleteThirdPartyIdentifiers.Response

    /**
     * @see [UnbindThirdPartyIdentifiers]
     */
    suspend fun unbindThirdPartyIdentifiers(context: MatrixEndpointContext<UnbindThirdPartyIdentifiers, UnbindThirdPartyIdentifiers.Request, UnbindThirdPartyIdentifiers.Response>): UnbindThirdPartyIdentifiers.Response

    /**
     * @see [GetOIDCRequestToken]
     */
    suspend fun getOIDCRequestToken(context: MatrixEndpointContext<GetOIDCRequestToken, Unit, GetOIDCRequestToken.Response>): GetOIDCRequestToken.Response

    /**
     * @see [Refresh]
     */
    suspend fun refresh(context: MatrixEndpointContext<Refresh, Refresh.Request, Refresh.Response>): Refresh.Response

    /**
     * @see [DeactivateAccount]
     */
    suspend fun getToken(context: MatrixEndpointContext<GetToken, RequestWithUIA<Unit>, ResponseWithUIA<GetToken.Response>>): ResponseWithUIA<GetToken.Response>
}