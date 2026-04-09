@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.utils

import de.connect2x.trixnity.idb.utils.IDBException.Operation.CLEAR
import de.connect2x.trixnity.idb.utils.IDBException.Operation.CURSOR
import de.connect2x.trixnity.idb.utils.IDBException.Operation.DELETE
import de.connect2x.trixnity.idb.utils.IDBException.Operation.GET
import de.connect2x.trixnity.idb.utils.IDBException.Operation.PUT
import js.objects.unsafeJso
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.IDBIndexParameters
import web.idb.IDBKeyRange
import web.idb.IDBRequest
import web.idb.IDBTransaction
import web.idb.IDBValidKey
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsArray
import kotlin.js.toJsString
import kotlin.js.undefined
import kotlin.js.unsafeCast

value class WrappedTransaction(val tx: IDBTransaction) {

    fun createObjectStore(
        database: IDBDatabase,
        name: String,
        keyPath: KeyPath? = null,
        autoIncrement: Boolean? = null
    ): WrappedObjectStore = WrappedObjectStore(database.createObjectStore(name, unsafeJso {
        this.autoIncrement = autoIncrement
        this.keyPath = when (keyPath) {
            null -> null
            is KeyPath.Single -> keyPath.value.toJsString()
            is KeyPath.Multiple -> keyPath.values.map(String::toJsString).toJsArray()
        }
    }))

    fun objectStore(name: String): WrappedObjectStore = WrappedObjectStore(tx.objectStore(name))

    fun WrappedObjectStore.createIndex(
        name: String, keyPath: KeyPath, unique: Boolean? = null, multiEntry: Boolean? = null
    ): WrappedIndex {
        val options = unsafeJso<IDBIndexParameters> {
            this.unique = unique
            this.multiEntry = multiEntry
        }

        return WrappedIndex(
            when (keyPath) {
                is KeyPath.Single -> store.createIndex(name, keyPath.value, options)
                is KeyPath.Multiple -> store.createIndex(
                    name, keyPath.values.map(String::toJsString).toJsArray(), options
                )
            }
        )
    }

    fun WrappedObjectStore.index(name: String): WrappedIndex = WrappedIndex(store.index(name))

    suspend fun <T : JsAny> WrappedObjectStore.get(key: IDBValidKey): T? =
        suspendCancellableCoroutine { continuation ->
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
        suspendCancellableCoroutine { continuation ->
            val request = key?.let { store.put(value, it) } ?: store.put(value)

            request.onsuccess = EventHandler {
                continuation.resume(Unit)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(IDBException.fromDom(PUT, event.target.error))
            }
        }

    suspend fun WrappedObjectStore.delete(key: IDBValidKey): Unit =
        suspendCancellableCoroutine { continuation ->
            val request = store.delete(key)

            request.onsuccess = EventHandler {
                continuation.resume(Unit)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(IDBException.fromDom(DELETE, event.target.error))
            }
        }

    suspend fun WrappedObjectStore.clear(): Unit = suspendCancellableCoroutine { continuation ->
        val request = store.clear()

        request.onsuccess = EventHandler {
            continuation.resume(Unit)
        }

        request.onerror = EventHandler { event ->
            continuation.resumeWithException(IDBException.fromDom(CLEAR, event.target.error))
        }
    }

    fun WrappedObjectStore.openCursor(key: IDBValidKey? = null): Flow<CursorItem> = callbackFlow {
        val request = key?.let { store.openCursor(it) } ?: store.openCursor()

        request.onsuccess = EventHandler { event ->
            val cursor = event.target.result

            if (cursor == null) {
                channel.close()
            } else {
                channel.trySend(CursorItem(cursor.primaryKey, cursor.value))
                cursor.`continue`()
            }
        }

        request.onerror = EventHandler { event ->
            channel.close(IDBException.fromDom(CURSOR, event.target.error))
        }

        awaitClose {
            request.onsuccess = null
            request.onerror = null
        }
    }

    fun WrappedIndex.openCursor(key: IDBValidKey? = null): Flow<CursorItem> = callbackFlow {
        val request = key?.let { index.openCursor(it) } ?: index.openCursor()

        request.onsuccess = EventHandler { event ->
            val cursor = event.target.result


            if (cursor == null) {
                channel.close()
            } else {
                channel.trySend(CursorItem(cursor.primaryKey, cursor.value))
                cursor.`continue`()
            }
        }

        request.onerror = EventHandler { event ->
            channel.close(IDBException.fromDom(CURSOR, event.target.error))
        }

        awaitClose {
            request.onsuccess = null
            request.onerror = null
        }
    }

    fun WrappedIndex.openCursor(key: IDBKeyRange? = null): Flow<CursorItem> = callbackFlow {
        val request = key?.let { index.openCursor(it) } ?: index.openCursor()

        request.onsuccess = EventHandler { event ->
            val cursor = event.target.result


            if (cursor == null) {
                channel.close()
            } else {
                channel.trySend(CursorItem(cursor.primaryKey, cursor.value))
                cursor.`continue`()
            }
        }

        request.onerror = EventHandler { event ->
            channel.close(IDBException.fromDom(CURSOR, event.target.error))
        }

        awaitClose {
            request.onsuccess = null
            request.onerror = null
        }
    }

    fun WrappedIndex.openKeyCursor(key: IDBValidKey? = null): Flow<IDBValidKey> = callbackFlow {
        val request = key?.let { index.openKeyCursor(it) } ?: index.openKeyCursor()

        request.onsuccess = EventHandler { event ->
            val cursor = event.target.result

            if (cursor == null) {
                channel.close()
            } else {
                channel.trySend(cursor.primaryKey)
                cursor.`continue`()
            }
        }

        request.onerror = EventHandler { event ->
            channel.close(IDBException.fromDom(CURSOR, event.target.error))
        }

        awaitClose {
            request.onsuccess = null
            request.onerror = null
        }
    }
}
