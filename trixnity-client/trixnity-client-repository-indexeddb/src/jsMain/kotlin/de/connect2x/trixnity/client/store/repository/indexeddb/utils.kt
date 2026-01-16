package de.connect2x.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.serializer

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