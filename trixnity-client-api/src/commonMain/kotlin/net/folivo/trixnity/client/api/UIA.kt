package net.folivo.trixnity.client.api

import io.ktor.http.*
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequest
import net.folivo.trixnity.client.api.model.uia.AuthenticationType
import net.folivo.trixnity.client.api.model.uia.UIAState

sealed interface UIA<T> {
    data class UIASuccess<T>(
        val value: T
    ) : UIA<T>

    data class UIAStep<T>(
        val state: UIAState,
        val getFallbackUrl: (AuthenticationType) -> Url,
        private val authenticateCallback: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        private val onSuccessCallback: suspend () -> Unit = {}
    ) : UIA<T> {
        suspend fun authenticate(request: AuthenticationRequest): Result<UIA<T>> =
            authenticateCallback(request).mapCatching {
                it.injectOnSuccessIntoUIA(onSuccessCallback)
            }
    }

    data class UIAError<T>(
        val state: UIAState,
        val errorResponse: ErrorResponse,
        val getFallbackUrl: (AuthenticationType) -> Url,
        private val authenticateCallback: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        private val onSuccessCallback: suspend () -> Unit = {}
    ) : UIA<T> {
        suspend fun authenticate(request: AuthenticationRequest): Result<UIA<T>> =
            authenticateCallback(request).mapCatching {
                it.injectOnSuccessIntoUIA(onSuccessCallback)
            }
    }
}

suspend fun <T> UIA<T>.injectOnSuccessIntoUIA(onSuccessCallback: suspend () -> Unit = {}): UIA<T> {
    return when (this) {
        is UIA.UIASuccess -> this.also { onSuccessCallback() }
        is UIA.UIAStep -> this.copy(onSuccessCallback = onSuccessCallback)
        is UIA.UIAError -> this.copy(onSuccessCallback = onSuccessCallback)
    }
}