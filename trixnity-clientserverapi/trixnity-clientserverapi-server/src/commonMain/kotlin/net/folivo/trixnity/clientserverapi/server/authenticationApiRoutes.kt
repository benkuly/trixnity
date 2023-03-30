package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.authenticationApiRoutes(
    handler: AuthenticationApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    matrixEndpoint(json, contentMappings, handler::whoAmI)
    matrixEndpoint(json, contentMappings, handler::isRegistrationTokenValid)
    matrixEndpoint(json, contentMappings, handler::isUsernameAvailable)
    matrixEndpoint(json, contentMappings, handler::getEmailRequestTokenForPassword)
    matrixEndpoint(json, contentMappings, handler::getEmailRequestTokenForRegistration)
    matrixEndpoint(json, contentMappings, handler::getMsisdnRequestTokenForPassword)
    matrixEndpoint(json, contentMappings, handler::getMsisdnRequestTokenForRegistration)
    matrixUIAEndpoint(json, contentMappings, handler::register)
    matrixEndpoint(json, contentMappings) {
        val redirectUrl = handler.ssoRedirect(this)
        call.response.header(HttpHeaders.Location, redirectUrl)
        call.response.status(HttpStatusCode.Found)
    }
    matrixEndpoint(json, contentMappings) {
        val redirectUrl = handler.ssoRedirectTo(this)
        call.response.header(HttpHeaders.Location, redirectUrl)
        call.response.status(HttpStatusCode.Found)
    }
    matrixEndpoint(json, contentMappings, handler::getLoginTypes)
    matrixEndpoint(json, contentMappings, handler::login)
    matrixEndpoint(json, contentMappings, handler::logout)
    matrixEndpoint(json, contentMappings, handler::logoutAll)
    matrixUIAEndpoint(json, contentMappings, handler::deactivateAccount)
    matrixUIAEndpoint(json, contentMappings, handler::changePassword)
    matrixEndpoint(json, contentMappings, handler::getThirdPartyIdentifiers)
    matrixUIAEndpoint(json, contentMappings, handler::addThirdPartyIdentifiers)
    matrixEndpoint(json, contentMappings, handler::bindThirdPartyIdentifiers)
    matrixEndpoint(json, contentMappings, handler::deleteThirdPartyIdentifiers)
    matrixEndpoint(json, contentMappings, handler::unbindThirdPartyIdentifiers)
    matrixEndpoint(json, contentMappings, handler::getOIDCRequestToken)
    matrixEndpoint(json, contentMappings, handler::refresh)
}