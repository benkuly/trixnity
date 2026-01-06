@file:OptIn(ExperimentalWasmJsInterop::class)

package net.folivo.trixnity.idb.schemaexporter

import js.objects.unsafeJso
import web.idb.IDBIndex
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal external interface IndexInfo : JsAny {
    var keyPath: JsAny
    var unique: Boolean
    var multiEntry: Boolean
}

internal fun IndexInfo(index: IDBIndex): IndexInfo {
    return unsafeJso {
        keyPath = index.keyPath
        unique = index.unique
        multiEntry = index.multiEntry
    }
}
