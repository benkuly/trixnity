package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import net.folivo.trixnity.core.ErrorResponse

class MatrixQueryParameterOrBearerAuthenticationProvider internal constructor(
    configuration: Configuration,
    private val field: String,
    private val token: String
) : AuthenticationProvider(configuration) {
    class Configuration internal constructor(name: String? = null) : Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val queryCredentials = context.call.request.queryParameters[field]
        val accessTokenCredentials = context.call.request.getAccessTokenFromHeader()
        val cause = when {
            queryCredentials == null && accessTokenCredentials == null -> AuthenticationFailedCause.NoCredentials
            accessTokenCredentials != null && queryCredentials != null && accessTokenCredentials != queryCredentials
                    || (accessTokenCredentials == null && queryCredentials != token)
                    || (queryCredentials == null && accessTokenCredentials != token)
                    || (accessTokenCredentials == queryCredentials && queryCredentials != token) -> AuthenticationFailedCause.InvalidCredentials

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

private fun ApplicationRequest.getAccessTokenFromHeader(): String? {
    return when (val authHeader = parseAuthorizationHeader()) {
        is HttpAuthHeader.Single -> {
            if (!authHeader.authScheme.equals("Bearer", ignoreCase = true)) null
            else authHeader.blob
        }

        else -> null
    }
}

fun AuthenticationConfig.matrixQueryParameterOrBearer(
    name: String? = null,
    field: String,
    token: String,
) {
    val provider =
        MatrixQueryParameterOrBearerAuthenticationProvider(
            MatrixQueryParameterOrBearerAuthenticationProvider.Configuration(name),
            field,
            token
        )
    register(provider)
}