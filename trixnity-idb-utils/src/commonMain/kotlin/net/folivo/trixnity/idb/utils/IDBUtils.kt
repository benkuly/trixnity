package net.folivo.trixnity.idb.utils

import js.errors.toThrowable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.indexedDB
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object IDBUtils {
    suspend fun openDatabase(
        name: String,
        version: Int,
        upgrade: (IDBDatabase, Int, Int?) -> Unit
    ): IDBDatabase = coroutineScope {
        val request = indexedDB.open(name, version.toDouble())

        suspendCancellableCoroutine { continuation ->

            request.onsuccess = EventHandler { event ->
                continuation.resume(event.target.result)
            }
            request.onblocked = EventHandler { event ->
                continuation.resumeWithException(
                    Error(
                        "database blocked",
                        event.target.error?.toThrowable()
                    )
                )
            }
            request.onerror = EventHandler { event ->
                continuation.resumeWithException(
                    Error(
                        "database error",
                        event.target.error?.toThrowable()
                    )
                )
            }
            request.onupgradeneeded = EventHandler { event ->
                upgrade(event.target.result, event.oldVersion.toInt(), event.newVersion?.toInt())
            }
        }
    }
}



