package de.connect2x.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.UserId

class MatrixAccessTokenAuth internal constructor(
    private val config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(name: String? = null) : AuthenticationProvider.Config(name) {
        var authenticationFunction: AccessTokenAuthenticationFunction = AccessTokenAuthenticationFunction {
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
                        ErrorResponse.UnknownToken("invalid token", authResult?.softLogout ?: false)
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

fun interface AccessTokenAuthenticationFunction {
    suspend operator fun invoke(credentials: UserAccessTokenCredentials): AccessTokenAuthenticationFunctionResult
}

data class MatrixClientPrincipal(val userId: UserId, val device: String)
data class UserAccessTokenCredentials(val accessToken: String)
data class AccessTokenAuthenticationFunctionResult(
    val principal: MatrixClientPrincipal?,
    val cause: AuthenticationFailedCause?,
    val softLogout: Boolean = false
)

fun ApplicationCall.matrixClientPrincipal() = principal<MatrixClientPrincipal>()
    ?: throw MatrixServerException(HttpStatusCode.Unauthorized, ErrorResponse.Unauthorized("no authorized"))

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
    val provider = MatrixAccessTokenAuth(
        MatrixAccessTokenAuth.Config(name)
            .apply(configure)
            .apply {
                skipWhen {
                    when (it.attributes.getOrNull(AuthRequired.attributeKey)) {
                        AuthRequired.YES -> false
                        AuthRequired.OPTIONAL -> !it.request.headers.contains(HttpHeaders.Authorization) &&
                                !it.request.queryParameters.contains("access_token")

                        AuthRequired.NO -> true
                        AuthRequired.NEVER -> true
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