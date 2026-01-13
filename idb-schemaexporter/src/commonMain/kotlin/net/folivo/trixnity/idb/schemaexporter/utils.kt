@file:OptIn(ExperimentalWasmJsInterop::class)

package net.folivo.trixnity.idb.schemaexporter

import js.collections.JsMap
import js.errors.toThrowable
import js.iterable.JsIterable
import js.iterable.JsIterator
import js.iterable.isYield
import js.objects.Object
import js.objects.ReadonlyRecord
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.indexedDB
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.js


internal fun <V : JsAny?> Map<JsString, V>.toRecord(): ReadonlyRecord<JsString, V> {
    val jsMap = JsMap<JsString, V>()

    for ((key, value) in this) {
        jsMap.set(key, value)
    }

    return Object.fromEntries(jsMap)
}

internal operator fun <T : JsAny?> JsIterable<T>.iterator(): Iterator<T> {
    val iterator = getJsIterator(this)
    return generateSequence {
        val result = iterator.next()
        if (isYield(result)) result else null
    }.map { it.value }
        .iterator()
}

internal fun stringify(value: JsAny): String = js("""JSON.stringify(value, null, "  ")""")

internal suspend fun db(name: String): IDBDatabase = suspendCoroutine { continuation ->
    val request = indexedDB.open(name)

    request.onsuccess = EventHandler {
        continuation.resume(request.result)
    }

    request.onblocked = EventHandler {
        continuation.resumeWithException(Error("blocked"))
    }

    request.onerror = EventHandler {
        continuation.resumeWithException(Error(request.error?.toThrowable()))
    }
}

private fun <T : JsAny?> getJsIterator(iterable: JsIterable<T>): JsIterator<T> =
    js("""iterable[Symbol.iterator]()""")
