@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.schemaexporter

import js.objects.ReadonlyRecord
import js.objects.unsafeJso
import js.string.JsStrings.toKotlinString
import web.idb.IDBDatabase
import web.idb.IDBTransactionMode
import web.idb.readonly
import kotlin.js.*

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
