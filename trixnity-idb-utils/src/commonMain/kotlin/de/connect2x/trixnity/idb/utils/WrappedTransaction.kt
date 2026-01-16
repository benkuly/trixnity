@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.utils

import js.errors.toThrowable
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
                continuation.resumeWithException(Error("get error", event.target.error?.toThrowable()))
            }
        }

    suspend fun <T : JsAny> WrappedObjectStore.put(value: T, key: IDBValidKey): Unit =
        suspendCoroutine { continuation ->
            val request = store.put(value, key)

            request.onsuccess = EventHandler {
                continuation.resume(Unit)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(Error("put error", event.target.error?.toThrowable()))
            }
        }

    suspend fun WrappedObjectStore.delete(key: IDBValidKey): Unit = suspendCoroutine { continuation ->
        val request = store.delete(key)

        request.onsuccess = EventHandler {
            continuation.resume(Unit)
        }

        request.onerror = EventHandler { event ->
            continuation.resumeWithException(Error("delete error", event.target.error?.toThrowable()))
        }
    }

    suspend fun WrappedObjectStore.clear(): Unit = suspendCoroutine { continuation ->
        val request = store.clear()

        request.onsuccess = EventHandler {
            continuation.resume(Unit)
        }

        request.onerror = EventHandler { event ->
            continuation.resumeWithException(Error("clear error", event.target.error?.toThrowable()))
        }
    }
}