package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import net.folivo.trixnity.client.rest.api.ErrorResponse

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
        MatrixQueryParameterAuthenticationProvider(
            net.folivo.trixnity.appservice.rest.MatrixQueryParameterAuthenticationProvider.Configuration(
                name
            )
        )

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->

        // FIXME simplify
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
                        ErrorResponse("401", "NET.FOLIVO.MATRIX_UNAUTHORIZED")

                    )
                    else -> call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("403", "NET.FOLIVO.MATRIX_FORBIDDEN")

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