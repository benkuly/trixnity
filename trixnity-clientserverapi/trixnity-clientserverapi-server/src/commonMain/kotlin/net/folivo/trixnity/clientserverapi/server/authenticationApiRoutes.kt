package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.authentication.*
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.authenticationApiRoutes(
    handler: AuthenticationApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<WhoAmI, Unit, WhoAmI.Response>(json, contentMappings) {
            handler.whoAmI(this)
        }
        matrixEndpoint<IsRegistrationTokenValid, IsRegistrationTokenValid.Response>(json, contentMappings) {
            handler.isRegistrationTokenValid(this)
        }
        matrixEndpoint<IsUsernameAvailable>(json, contentMappings) {
            handler.isUsernameAvailable(this)
        }
        matrixEndpoint<GetEmailRequestTokenForPassword, GetEmailRequestTokenForPassword.Request, GetEmailRequestTokenForPassword.Response>(
            json,
            contentMappings
        ) {
            handler.getEmailRequestTokenForPassword(this)
        }
        matrixEndpoint<GetEmailRequestTokenForRegistration, GetEmailRequestTokenForRegistration.Request, GetEmailRequestTokenForRegistration.Response>(
            json,
            contentMappings
        ) {
            handler.getEmailRequestTokenForRegistration(this)
        }
        matrixEndpoint<GetMsisdnRequestTokenForPassword, GetMsisdnRequestTokenForPassword.Request, GetMsisdnRequestTokenForPassword.Response>(
            json,
            contentMappings
        ) {
            handler.getMsisdnRequestTokenForPassword(this)
        }
        matrixEndpoint<GetMsisdnRequestTokenForRegistration, GetMsisdnRequestTokenForRegistration.Request, GetMsisdnRequestTokenForRegistration.Response>(
            json,
            contentMappings
        ) {
            handler.getMsisdnRequestTokenForRegistration(this)
        }
        matrixUIAEndpoint<Register, Register.Request, Register.Response>(json, contentMappings) {
            handler.register(this)
        }
        matrixEndpoint<GetLoginTypes, GetLoginTypes.Response>(json, contentMappings) {
            handler.getLoginTypes(this)
        }
        matrixEndpoint<Login, Login.Request, Login.Response>(json, contentMappings) {
            handler.login(this)
        }
        matrixEndpoint<Logout>(json, contentMappings) {
            handler.logout(this)
        }
        matrixEndpoint<LogoutAll>(json, contentMappings) {
            handler.logoutAll(this)
        }
        matrixUIAEndpoint<DeactivateAccount, DeactivateAccount.Request, DeactivateAccount.Response>(
            json,
            contentMappings
        ) {
            handler.deactivateAccount(this)
        }
        matrixUIAEndpoint<ChangePassword, ChangePassword.Request, Unit>(json, contentMappings) {
            handler.changePassword(this)
        }
        matrixEndpoint<GetThirdPartyIdentifiers, Unit, GetThirdPartyIdentifiers.Response>(json, contentMappings) {
            handler.getThirdPartyIdentifiers(this)
        }
        matrixUIAEndpoint<AddThirdPartyIdentifiers, AddThirdPartyIdentifiers.Request, Unit>(json, contentMappings) {
            handler.addThirdPartyIdentifiers(this)
        }
        matrixEndpoint<BindThirdPartyIdentifiers, BindThirdPartyIdentifiers.Request>(json, contentMappings) {
            handler.bindThirdPartyIdentifiers(this)
        }
        matrixEndpoint<DeleteThirdPartyIdentifiers, DeleteThirdPartyIdentifiers.Request, DeleteThirdPartyIdentifiers.Response>(
            json,
            contentMappings
        ) {
            handler.deleteThirdPartyIdentifiers(this)
        }
        matrixEndpoint<UnbindThirdPartyIdentifiers, UnbindThirdPartyIdentifiers.Request, UnbindThirdPartyIdentifiers.Response>(
            json,
            contentMappings
        ) {
            handler.unbindThirdPartyIdentifiers(this)
        }
        matrixEndpoint<GetOIDCRequestToken, GetOIDCRequestToken.Response>(json, contentMappings) {
            handler.getOIDCRequestToken(this)
        }
    }
}