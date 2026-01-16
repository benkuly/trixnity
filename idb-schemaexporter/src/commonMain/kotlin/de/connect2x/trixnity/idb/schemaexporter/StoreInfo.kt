@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.schemaexporter

import js.core.JsPrimitives.toKotlinString
import js.objects.ReadonlyRecord
import js.objects.unsafeJso
import web.idb.IDBIndex
import web.idb.IDBObjectStore
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString

internal external interface StoreInfo : JsAny {
    var keyPath: JsAny?
    var autoIncrement: Boolean
    var indexes: ReadonlyRecord<JsString, IndexInfo>
}

internal fun StoreInfo(store: IDBObjectStore): StoreInfo {
    return unsafeJso {
        keyPath = store.keyPath
        autoIncrement = store.autoIncrement
        indexes = store.indexNames
            .iterator()
            .asSequence()
            .associateWith {
                IndexInfo(store.index(it.toKotlinString()))
            }
            .toRecord()
    }
}
