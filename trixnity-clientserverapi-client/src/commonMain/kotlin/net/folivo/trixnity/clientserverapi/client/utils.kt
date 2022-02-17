package net.folivo.trixnity.clientserverapi.client

import io.ktor.http.*
import kotlinx.coroutines.delay
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.MatrixId
import kotlin.reflect.KClass

internal fun unsupportedEventType(eventType: KClass<*>): String =
    "Event type ${eventType.simpleName} is not supported. If it is a custom type, you should register it in MatrixRestClient. " +
            "If not, ensure, that you use the generic fields (e. g. sendStateEvent<MemberEventContent>(...)) " +
            "so that we can determine the right event type."

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}

fun EventId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}

fun String.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return this.encodeURLQueryComponent(true)
}

suspend fun <T> retryResultOnRateLimit(block: suspend () -> Result<T>): Result<T> {
    val result = block()
    val exception = result.exceptionOrNull()
    return if (exception is MatrixServerException && exception.errorResponse is net.folivo.trixnity.clientserverapi.model.ErrorResponse.LimitExceeded) {
        delay(exception.errorResponse.retryAfterMillis)
        block()
    } else result
}

suspend fun <T> retryOnRateLimit(block: suspend () -> T): T {
    return try {
        block()
    } catch (e: MatrixServerException) {
        if (e.errorResponse is net.folivo.trixnity.clientserverapi.model.ErrorResponse.LimitExceeded) {
            delay(e.errorResponse.retryAfterMillis)
            block()
        } else throw e
    }
}