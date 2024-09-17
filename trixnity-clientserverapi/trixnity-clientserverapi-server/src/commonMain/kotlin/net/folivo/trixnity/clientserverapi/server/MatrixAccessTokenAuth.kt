package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import net.folivo.trixnity.api.server.AuthRequired
import net.folivo.trixnity.api.server.withoutAuthAttributeKey
import net.folivo.trixnity.core.ErrorResponse

class MatrixAccessTokenAuth internal constructor(
    private val config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(name: String? = null) : AuthenticationProvider.Config(name) {
        var authenticationFunction: AccessTokenAuthenticationFunction = {
            throw NotImplementedError("MatrixAccessTokenAuth validate function is not specified.")
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val credentials = call.request.getAccessToken()
        val authResult = credentials?.let { config.authenticationFunction(it) }
        val principal = authResult?.principal

        val cause = when {
            credentials == null || authResult == null -> NoCredentials
            authResult.cause != null -> authResult.cause
            principal == null -> InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge("MatrixAccessTokenAuth", cause) { challenge, challengeCall ->
                when (cause) {
                    NoCredentials ->
                        challengeCall.respond<ErrorResponse>(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse.MissingToken("missing token")
                        )

                    InvalidCredentials -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.UnknownToken("invalid token")
                    )

                    is Error -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse.Unknown(cause.message)
                    )
                }
                challenge.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }
}

typealias AccessTokenAuthenticationFunction = suspend (UserAccessTokenCredentials) -> AccessTokenAuthenticationFunctionResult

data class UserAccessTokenCredentials(val accessToken: String) : Credential
data class AccessTokenAuthenticationFunctionResult(val principal: Principal?, val cause: AuthenticationFailedCause?)

private fun ApplicationRequest.getAccessToken(): UserAccessTokenCredentials? {
    return when (val authHeader = parseAuthorizationHeader()) {
        is HttpAuthHeader.Single -> {
            if (!authHeader.authScheme.equals("Bearer", ignoreCase = true)) null
            else authHeader.blob
        }

        else -> queryParameters["access_token"]
    }?.let { UserAccessTokenCredentials(it) }
}

fun AuthenticationConfig.matrixAccessTokenAuth(
    name: String? = null,
    configure: MatrixAccessTokenAuth.Config.() -> Unit
) {
    val provider = MatrixAccessTokenAuth(MatrixAccessTokenAuth.Config(name)
        .apply(configure)
        .apply {
            skipWhen {
                when (it.attributes.getOrNull(withoutAuthAttributeKey)) {
                    AuthRequired.YES -> false
                    AuthRequired.OPTIONAL -> !it.request.headers.contains(HttpHeaders.Authorization) &&
                            !it.request.queryParameters.contains("access_token")

                    AuthRequired.NO -> true
                    null -> false
                }
            }
        })
    register(provider)
}

fun Application.installMatrixAccessTokenAuth(
    name: String? = null,
    configure: MatrixAccessTokenAuth.Config.() -> Unit
) {
    install(Authentication) {
        matrixAccessTokenAuth(name, configure)
    }
}