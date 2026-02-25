@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.serializer
import web.idb.IDBValidKey

@OptIn(ExperimentalSerializationApi::class)
internal fun <T : Any> Json.decodeFromDynamicNullable(
    serializer: KSerializer<T>,
    value: dynamic,
): T? =
    if (value == null) null
    else decodeFromDynamic(serializer, value)

internal inline fun <reified T : Any> Json.decodeFromDynamicNullable(
    value: dynamic,
): T? = decodeFromDynamicNullable(serializer(), value)


internal fun keyOf(keys: Array<String>): IDBValidKey = when (keys.size) {
    0 -> error("invalid key")
    1 -> IDBValidKey(keys.first())
    else -> IDBValidKey(keys.map(::IDBValidKey).toJsArray())
}

internal fun keyOf(vararg keys: String?): IDBValidKey = keyOf(keys.filterNotNull().toTypedArray())
