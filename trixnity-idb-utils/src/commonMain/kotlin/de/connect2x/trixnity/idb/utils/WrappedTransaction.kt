@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.utils

import de.connect2x.trixnity.idb.utils.IDBException.Operation.CLEAR
import de.connect2x.trixnity.idb.utils.IDBException.Operation.DELETE
import de.connect2x.trixnity.idb.utils.IDBException.Operation.GET
import de.connect2x.trixnity.idb.utils.IDBException.Operation.PUT
import web.events.EventHandler
import web.idb.IDBRequest
import web.idb.IDBTransaction
import web.idb.IDBValidKey
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.undefined
import kotlin.js.unsafeCast

value class WrappedTransaction(val tx: IDBTransaction) {

    fun objectStore(name: String): WrappedObjectStore = WrappedObjectStore(tx.objectStore(name))

    suspend fun <T : JsAny> WrappedObjectStore.get(key: IDBValidKey): T? =
        suspendCoroutine { continuation ->
            val request = store.get(key).unsafeCast<IDBRequest<JsAny?>>()

            request.onsuccess = EventHandler { event ->
                val value = when (val any = event.target.result) {
                    undefined -> null
                    else -> any.unsafeCast<T>()
                }

                continuation.resume(value)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(IDBException.fromDom(GET, event.target.error))
            }
        }

    suspend fun <T : JsAny> WrappedObjectStore.put(value: T, key: IDBValidKey? = null): Unit =
        suspendCoroutine { continuation ->
            val request = key?.let { store.put(value, it) } ?: store.put(value)

            request.onsuccess = EventHandler {
                continuation.resume(Unit)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(IDBException.fromDom(PUT, event.target.error))
            }
        }

    suspend fun WrappedObjectStore.delete(key: IDBValidKey): Unit = suspendCoroutine { continuation ->
        val request = store.delete(key)

        request.onsuccess = EventHandler {
            continuation.resume(Unit)
        }

        request.onerror = EventHandler { event ->
            continuation.resumeWithException(IDBException.fromDom(DELETE, event.target.error))
        }
    }

    suspend fun WrappedObjectStore.clear(): Unit = suspendCoroutine { continuation ->
        val request = store.clear()

        request.onsuccess = EventHandler {
            continuation.resume(Unit)
        }

        request.onerror = EventHandler { event ->
            continuation.resumeWithException(IDBException.fromDom(CLEAR, event.target.error))
        }
    }
}