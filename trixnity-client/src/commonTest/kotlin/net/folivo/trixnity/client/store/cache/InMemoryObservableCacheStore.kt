package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

open class InMemoryObservableCacheStore<K, V> : ObservableCacheStore<K, V> {
    val values = MutableStateFlow(mapOf<K, V>())
    override suspend fun get(key: K): V? = values.value[key]

    override suspend fun persist(key: K, value: V?) {
        if (value == null) values.update { it - key }
        else values.update { it + (key to value) }
    }

    override suspend fun deleteAll() {
        values.value = emptyMap()
    }
}