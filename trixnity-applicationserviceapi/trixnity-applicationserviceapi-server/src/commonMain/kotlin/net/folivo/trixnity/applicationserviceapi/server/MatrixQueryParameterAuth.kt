package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import net.folivo.trixnity.core.ErrorResponse

class MatrixQueryParameterAuthenticationProvider internal constructor(
    configuration: Configuration,
    private val field: String,
    private val token: String
) : AuthenticationProvider(configuration) {
    class Configuration internal constructor(name: String? = null) : Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        // TODO simplify
        val credentials = context.call.request.queryParameters[field]
        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            credentials != token -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge("MatrixQueryParameterAuth", cause) { challenge, call ->
                when (cause) {
                    AuthenticationFailedCause.NoCredentials ->
                        call.respond<ErrorResponse>(HttpStatusCode.Unauthorized, ErrorResponse.Unauthorized())
                    else -> call.respond<ErrorResponse>(HttpStatusCode.Forbidden, ErrorResponse.Forbidden())
                }
                challenge.complete()
            }
        } else {
            context.principal(UserIdPrincipal("homeserver"))
        }
    }
}

fun AuthenticationConfig.matrixQueryParameter(
    name: String? = null,
    field: String,
    token: String,
) {
    val provider =
        MatrixQueryParameterAuthenticationProvider(
            MatrixQueryParameterAuthenticationProvider.Configuration(name),
            field,
            token
        )
    register(provider)
}