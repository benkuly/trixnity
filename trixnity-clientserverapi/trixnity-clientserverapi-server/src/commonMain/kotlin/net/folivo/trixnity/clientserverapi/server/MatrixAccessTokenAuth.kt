package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import net.folivo.trixnity.api.server.withoutAuthAttributeKey
import net.folivo.trixnity.core.ErrorResponse

class MatrixAccessTokenAuth internal constructor(
    config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(name: String? = null) : AuthenticationProvider.Config(name) {
        internal var authenticationFunction: AccessTokenAuthenticationFunction = {
            throw NotImplementedError("MatrixAccessTokenAuth validate function is not specified.")
        }
    }

    internal val authenticationFunction = config.authenticationFunction

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val credentials = call.request.getAccessToken()
        val authResult = credentials?.let { authenticationFunction(it) }
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
                        challengeCall.respond<ErrorResponse>(HttpStatusCode.Unauthorized, ErrorResponse.MissingToken())
                    InvalidCredentials -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.UnknownToken()
                    )
                    is Error -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.UnknownToken(cause.message)
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

fun ApplicationRequest.getAccessToken(): UserAccessTokenCredentials? {
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
                it.attributes.getOrNull(withoutAuthAttributeKey) == true
            }
        })
    register(provider)
}