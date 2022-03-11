package net.folivo.trixnity.api.client

import io.ktor.http.*
import kotlinx.coroutines.delay
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

fun UserId.e(): UserId { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed
    return UserId(full.encodeURLQueryComponent(true))
}

fun EventId.e(): EventId { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed
    return EventId(full.encodeURLQueryComponent(true))
}

fun RoomId.e(): RoomId { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed
    return RoomId(full.encodeURLQueryComponent(true))
}

fun RoomAliasId.e(): RoomAliasId { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed
    return RoomAliasId(full.encodeURLQueryComponent(true))
}

fun String.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-3953 is fixed
    return this.encodeURLQueryComponent(true)
}

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