package net.folivo.trixnity.appservice

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

class MatrixQueryParameterAuthenticationProvider internal constructor(
    configuration: Configuration
) : AuthenticationProvider(configuration) {
    class Configuration internal constructor(name: String) : AuthenticationProvider.Configuration(name)
}

fun Authentication.Configuration.matrixQueryParameter(
    name: String,
    field: String,
    token: String,
) {
    val provider =
        MatrixQueryParameterAuthenticationProvider(MatrixQueryParameterAuthenticationProvider.Configuration(name))

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->

        // TODO simplify
        val credentials = call.request.queryParameters[field]
        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            credentials != token -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge("MatrixQueryParameterAuth", cause) {
                when (cause) {
                    AuthenticationFailedCause.NoCredentials -> call.respond(
                        HttpStatusCode.Unauthorized,
                        net.folivo.trixnity.clientserverapi.model.ErrorResponse.Unauthorized()

                    )
                    else -> call.respond(
                        HttpStatusCode.Forbidden,
                        net.folivo.trixnity.clientserverapi.model.ErrorResponse.Forbidden()

                    )
                }
                it.complete()
            }
        } else {
            context.principal(UserIdPrincipal("homeserver"))
        }
    }

    register(provider)
}