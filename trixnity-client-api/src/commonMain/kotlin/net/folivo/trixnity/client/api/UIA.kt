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
        val authenticate: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        val getFallbackUrl: (AuthenticationType) -> Url
    ) : UIA<T>

    data class UIAError<T>(
        val state: UIAState,
        val errorResponse: ErrorResponse,
        val authenticate: suspend (AuthenticationRequest) -> Result<UIA<T>>,
        val getFallbackUrl: (AuthenticationType) -> Url
    ) : UIA<T>
}