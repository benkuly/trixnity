@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.schemaexporter

import kotlin.js.ExperimentalWasmJsInterop

suspend fun exportSchema(databaseName: String): String {
    return stringify(DatabaseInfo(db(databaseName)))
}