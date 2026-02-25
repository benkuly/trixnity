@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.utils

import web.idb.IDBValidKey
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

data class CursorItem(
    val primaryKey: IDBValidKey,
    val value: JsAny?,
) {
    companion object
}