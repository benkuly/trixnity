@file:OptIn(ExperimentalWasmJsInterop::class)

package net.folivo.trixnity.idb.utils

import js.errors.toThrowable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.IDBTransaction
import web.idb.IDBTransactionMode
import web.idb.readonly
import web.idb.readwrite
import kotlin.Array
import kotlin.Error
import kotlin.OptIn
import kotlin.String
import kotlin.Unit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsArray
import kotlin.js.toJsString

suspend fun <T> IDBDatabase.readTransaction(
    vararg names: String,
    block: suspend WrappedTransaction.() -> T
): T =
    readTransaction(
        names = names,
        mode = IDBTransactionMode.readonly,
        block = block,
    )

suspend fun <T> IDBDatabase.writeTransaction(
    vararg names: String,
    block: suspend WrappedTransaction.() -> T
): T =
    readTransaction(
        names = names,
        mode = IDBTransactionMode.readwrite,
        block = block,
    )


private suspend fun <T> IDBDatabase.readTransaction(
    names: Array<out String>,
    mode: IDBTransactionMode,
    block: suspend WrappedTransaction.() -> T
): T =
    coroutineScope {
        val tx = transaction(names.map { it.toJsString() }.toJsArray(), mode)

        launch(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine { continuation ->

                continuation.invokeOnCancellation {
                    tx.abort()
                }

                tx.onerror = EventHandler { event ->
                    continuation.resumeWithException(Error(event.target.error?.toThrowable()))
                }
                tx.oncomplete = EventHandler {
                    continuation.resume(Unit)
                }
                tx.onabort = EventHandler {
                    continuation.resumeWithException(Error("transaction aborted"))
                }
            }
        }

        WrappedTransaction(tx).block()
    }
