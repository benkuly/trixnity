package net.folivo.trixnity.clientserverapi.client

import io.ktor.http.*
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationType
import net.folivo.trixnity.clientserverapi.model.uia.UIAState
import net.folivo.trixnity.core.ErrorResponse

sealed interface UIA<T> {
    data class Success<T>(
        val value: T
    ) : UIA<T>

    data class Step<T>(
        val state: UIAState,
        private val getFallbackUrlCallback: (AuthenticationType) -> Url,
        private val authenticateCallback: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        private val onSuccessCallback: suspend () -> Unit = {}
    ) : UIA<T> {
        fun getFallbackUrl(authenticationType: AuthenticationType): Url =
            getFallbackUrlCallback(authenticationType)

        suspend fun authenticate(request: AuthenticationRequest): Result<UIA<T>> =
            authenticateCallback(request).mapCatching {
                it.injectOnSuccessIntoUIA(onSuccessCallback)
            }
    }

    data class Error<T>(
        val state: UIAState,
        val errorResponse: ErrorResponse,
        private val getFallbackUrlCallback: (AuthenticationType) -> Url,
        private val authenticateCallback: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        private val onSuccessCallback: suspend () -> Unit = {}
    ) : UIA<T> {
        fun getFallbackUrl(authenticationType: AuthenticationType): Url =
            getFallbackUrlCallback(authenticationType)

        suspend fun authenticate(request: AuthenticationRequest): Result<UIA<T>> =
            authenticateCallback(request).mapCatching {
                it.injectOnSuccessIntoUIA(onSuccessCallback)
            }
    }
}

suspend fun <T> UIA<T>.injectOnSuccessIntoUIA(onSuccessCallback: suspend () -> Unit = {}): UIA<T> {
    return when (this) {
        is UIA.Success -> this.also { onSuccessCallback() }
        is UIA.Step -> this.copy(onSuccessCallback = onSuccessCallback)
        is UIA.Error -> this.copy(onSuccessCallback = onSuccessCallback)
    }
}