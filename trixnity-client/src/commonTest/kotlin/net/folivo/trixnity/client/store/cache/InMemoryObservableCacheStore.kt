package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

open class InMemoryObservableCacheStore<K, V> : ObservableCacheStore<K, V> {
    var readDelay = Duration.ZERO
    var writeDelay = Duration.ZERO

    val values = MutableStateFlow(mapOf<K, V>())
    override suspend fun get(key: K): V? {
        delay(readDelay)
        return values.value[key]
    }

    override suspend fun persist(key: K, value: V?) {
        delay(writeDelay)
        if (value == null) values.update { it - key }
        else values.update { it + (key to value) }
    }

    override suspend fun deleteAll() {
        delay(writeDelay)
        values.value = emptyMap()
    }
}