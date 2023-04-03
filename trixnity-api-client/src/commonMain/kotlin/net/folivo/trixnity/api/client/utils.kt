package net.folivo.trixnity.api.client

import kotlinx.coroutines.delay
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException

suspend fun <T> retryResultOnRateLimit(block: suspend () -> Result<T>): Result<T> {
    val result = block()
    val exception = result.exceptionOrNull()
    if (exception !is MatrixServerException) return result
    val errorResponse = exception.errorResponse
    if (errorResponse !is ErrorResponse.LimitExceeded) return result
    delay(errorResponse.retryAfterMillis)
    return block()
}

suspend fun <T> retryOnRateLimit(block: suspend () -> T): T {
    return try {
        block()
    } catch (exception: MatrixServerException) {
        val errorResponse = exception.errorResponse
        if (errorResponse !is ErrorResponse.LimitExceeded) throw exception
        delay(errorResponse.retryAfterMillis)
        block()
    }
}