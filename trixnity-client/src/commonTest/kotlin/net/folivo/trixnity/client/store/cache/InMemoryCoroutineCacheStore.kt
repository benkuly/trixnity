package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

open class InMemoryCoroutineCacheStore<K, V> : CoroutineCacheStore<K, V> {
    val values = MutableStateFlow(mapOf<K, V>())
    val persisted = MutableStateFlow(mapOf<K, MutableStateFlow<Boolean>>())
    override suspend fun get(key: K): V? = values.value[key]

    override suspend fun persist(key: K, value: V?): StateFlow<Boolean>? {
        if (value == null) values.update { it - key }
        else values.update { it + (key to value) }
        return persisted.value[key]
    }

    override suspend fun deleteAll() {
        values.value = emptyMap()
    }
}