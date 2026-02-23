package de.connect2x.trixnity.client.store.repository.indexeddb

import js.json.parse
import js.json.stringify
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import web.idb.IDBValidKey
import kotlin.js.JsAny
import kotlin.js.toJsArray

internal fun <T : Any> Json.decodeFromDynamic(
    serializer: KSerializer<T>,
    value: JsAny,
): T = decodeFromString(serializer, stringify(value))

internal fun <T : Any> Json.encodeToDynamic(
    serializer: KSerializer<T>,
    value: T,
): JsAny = parse(encodeToString(serializer, value))

internal inline fun <reified T : Any> Json.encodeToDynamic(
    value: T,
): JsAny = parse(encodeToString(serializer(), value))


@OptIn(ExperimentalSerializationApi::class)
internal fun <T : Any> Json.decodeFromDynamicNullable(
    serializer: KSerializer<T>,
    value: JsAny?,
): T? =
    if (value == null) null
    else decodeFromDynamic(serializer, value)

internal inline fun <reified T : Any> Json.decodeFromDynamicNullable(
    value: JsAny?,
): T? = decodeFromDynamicNullable(serializer(), value)


internal fun keyOf(keys: Array<String>): IDBValidKey = when (keys.size) {
    0 -> error("invalid key")
    1 -> IDBValidKey(keys.first())
    else -> IDBValidKey(keys.map(::IDBValidKey).toJsArray())
}

internal fun keyOf(vararg keys: String?): IDBValidKey = keyOf(keys.filterNotNull().toTypedArray())
