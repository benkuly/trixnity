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
        matrixEndpoint<IsUsernameAvailable>(json, contentMappings) {
            handler.isUsernameAvailable(this)
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
    }
}