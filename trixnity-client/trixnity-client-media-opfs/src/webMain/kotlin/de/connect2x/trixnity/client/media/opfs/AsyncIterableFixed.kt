package de.connect2x.trixnity.client.media.opfs

import js.disposable.internal.SuspendCloseable
import js.function.unsafeInvoke
import js.iterable.AsyncIterator
import js.iterable.isYield
import js.objects.PropertyKey
import js.objects.ReadonlyRecord
import js.promise.Promise
import js.promise.await
import js.reflect.Reflect
import js.reflect.unsafeCast
import js.symbol.Symbol
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.js.JsAny
import kotlin.js.unsafeCast

// CODE IS ADAPTED FROM https://github.com/JetBrains/kotlin-wrappers/commit/2cec3cecc66ced40291918efda98f83d95cbfbd7
// TODO: remove when updating to Kotlin Wrappers 2026.1.1 or higher

internal external interface AsyncIterableFixed<out T : JsAny?> : JsAny

internal fun <T : JsAny?> AsyncIterableFixed<T>.asFlow(): Flow<T> = flow {
    val iterator = this@asFlow[Symbol.asyncIterator].call(this@asFlow)

    val closeable = SuspendCloseable {
        iterator.awaitFirst(
            PropertyKey("return"),
            Symbol.asyncDispose,
        )
    }

    closeable.use {
        do {
            val result = iterator.next().await()
            val done = if (isYield(result)) {
                emit(result.value)
                true
            } else {
                false
            }
        } while (done)
    }
}

private external interface Function<C : JsAny, R : JsAny?> : JsAny {
    fun call(thisArg: C): R
}

private operator fun <T : JsAny?> AsyncIterableFixed<T>.get(
    key: Symbol.asyncIterator,
): Function<AsyncIterableFixed<T>, AsyncIterator<T>> = checkNotNull(
    Reflect.get(
        this, key
    )
).unsafeCast<Function<AsyncIterableFixed<T>, AsyncIterator<T>>>()

private suspend fun JsAny.awaitFirst(
    vararg methodKeys: PropertyKey?,
) {
    val record = unsafeCast<ReadonlyRecord<PropertyKey, AsyncDispose?>>(this)

    val dispose = methodKeys.filterNotNull().firstNotNullOf { record[it] }.bind(this)

    val result = unsafeInvoke<JsAny?>(dispose)
    result as Promise<*>
    result.await()
}

private external interface AsyncDispose : JsAny {
    fun bind(thisArg: JsAny): AsyncDispose
}

@OptIn(ExperimentalContracts::class)
private suspend inline fun <R> SuspendCloseable.use(
    block: () -> R,
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    var exception: Throwable? = null
    try {
        return block()
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        withContext(NonCancellable) {
            closeFinally(exception)
        }
    }
}

private suspend fun SuspendCloseable.closeFinally(
    cause: Throwable?,
) {
    when {
        cause == null -> close()
        else -> try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
    }
}
