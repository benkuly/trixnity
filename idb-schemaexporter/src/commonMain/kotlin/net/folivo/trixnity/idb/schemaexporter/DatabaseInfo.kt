@file:OptIn(ExperimentalWasmJsInterop::class)

package net.folivo.trixnity.idb.schemaexporter

import js.core.JsPrimitives.toKotlinString
import js.objects.ReadonlyRecord
import js.objects.unsafeJso
import web.idb.IDBDatabase
import web.idb.IDBTransactionMode
import web.idb.readonly
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.toJsArray
import kotlin.js.toJsNumber

internal external interface DatabaseInfo : JsAny {
    var version: JsNumber
    var name: String
    var stores: ReadonlyRecord<JsString, StoreInfo>
}

internal fun DatabaseInfo(database: IDBDatabase): DatabaseInfo {
    val tx = database.transaction(
        database.objectStoreNames.iterator().asSequence().toList().toJsArray(),
        IDBTransactionMode.readonly
    )

    return unsafeJso {
        name = database.name
        version = database.version.toJsNumber()
        stores = database.objectStoreNames
            .iterator()
            .asSequence()
            .associateWith {
                StoreInfo(tx.objectStore(it.toKotlinString()))
            }
            .toRecord()
    }
}
