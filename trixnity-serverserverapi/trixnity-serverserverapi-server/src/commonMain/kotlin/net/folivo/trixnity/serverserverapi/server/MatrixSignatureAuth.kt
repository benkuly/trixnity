package net.folivo.trixnity.serverserverapi.server

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.AuthenticationFailedCause.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import net.folivo.trixnity.api.server.withoutAuthAttributeKey
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.serverserverapi.model.requestAuthenticationBody

class MatrixSignatureAuth internal constructor(
    private val config: Config,
) : AuthenticationProvider(config) {
    class Config internal constructor(name: String? = null, val hostname: String) :
        AuthenticationProvider.Config(name) {
        internal var authenticationFunction: SignatureAuthenticationFunction = {
            throw NotImplementedError("MatrixSignatureAuth validate function is not specified.")
        }
    }

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val credentials = call.request.getSignature(config.hostname)
        val authResult = credentials?.let { config.authenticationFunction(it) }
        val principal = authResult?.principal

        val cause = when {
            credentials == null || authResult == null -> NoCredentials
            authResult.cause != null -> authResult.cause
            principal == null -> InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge("MatrixSignatureAuth", cause) { challenge, challengeCall ->
                when (cause) {
                    NoCredentials ->
                        challengeCall.respond<ErrorResponse>(
                            HttpStatusCode.Unauthorized,
                            ErrorResponse.Unauthorized("missing signature")
                        )
                    InvalidCredentials -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.Unauthorized("wrong signature")
                    )
                    is Error -> challengeCall.respond<ErrorResponse>(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse.Unauthorized(cause.message)
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

data class SignedRequestAuthenticationBody(
    val signed: String,
    val signature: Key.Ed25519Key,
    val origin: String,
)

data class SignatureAuthenticationFunctionResult(val principal: Principal?, val cause: AuthenticationFailedCause?)

typealias SignatureAuthenticationFunction = suspend (SignedRequestAuthenticationBody) -> SignatureAuthenticationFunctionResult

private suspend fun ApplicationRequest.getSignature(hostname: String): SignedRequestAuthenticationBody? {
    return when (val authHeader = parseAuthorizationHeader()) {
        is HttpAuthHeader.Parameterized -> {
            if (!authHeader.authScheme.equals("X-Matrix", ignoreCase = true)) null
            else {
                val origin = authHeader.parameter("origin") ?: return null
                val (keyAlgorithm, keyId) = authHeader.parameter("key")?.let {
                    KeyAlgorithm.of(it.substringBefore(":")) to it.substringAfter(":")
                } ?: return null
                val signatureValue = authHeader.parameter("sig") ?: return null
                val signature =
                    when (keyAlgorithm) {
                        is KeyAlgorithm.Ed25519 -> Key.Ed25519Key(keyId, signatureValue)
                        else -> return null
                    }
                SignedRequestAuthenticationBody(
                    signed = requestAuthenticationBody(
                        content = call.receiveOrNull<ByteReadChannel>()?.toByteArray()?.decodeToString(),
                        destination = hostname,
                        method = httpMethod.value,
                        origin = origin,
                        uri = uri
                    ),
                    signature = signature,
                    origin = origin
                )
            }
        }
        else -> null
    }
}

fun AuthenticationConfig.matrixSignatureAuth(
    name: String? = null,
    hostname: String,
    configure: MatrixSignatureAuth.Config.() -> Unit
) {
    val provider = MatrixSignatureAuth(MatrixSignatureAuth.Config(name, hostname)
        .apply(configure)
        .apply {
            skipWhen {
                it.attributes.getOrNull(withoutAuthAttributeKey) == true
            }
        })
    register(provider)
}

fun Application.installMatrixSignatureAuth(
    name: String? = null,
    hostname: String,
    configure: MatrixSignatureAuth.Config.() -> Unit
) {
    install(DoubleReceive)
    install(Authentication) {
        matrixSignatureAuth(name, hostname, configure)
    }
}