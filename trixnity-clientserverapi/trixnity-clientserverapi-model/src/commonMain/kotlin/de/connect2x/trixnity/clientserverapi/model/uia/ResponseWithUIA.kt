package de.connect2x.trixnity.clientserverapi.model.uia

import de.connect2x.trixnity.core.ErrorResponse

sealed interface ResponseWithUIA<T> {
    data class Success<T>(
        val value: T
    ) : ResponseWithUIA<T>

    data class Step<T>(
        val state: UIAState,
    ) : ResponseWithUIA<T>

    data class Error<T>(
        val state: UIAState,
        val errorResponse: ErrorResponse,
    ) : ResponseWithUIA<T>
}