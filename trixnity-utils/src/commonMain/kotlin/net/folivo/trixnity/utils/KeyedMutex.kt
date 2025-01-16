package net.folivo.trixnity.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

open class KeyedMutex<K : Any>(concurrency: Int = 8) {
    init {
        require(concurrency > 0)
    }

    @PublishedApi
    internal val mutexes = Array(concurrency) { Mutex() }

    @OptIn(ExperimentalContracts::class)
    suspend inline fun <R> withLock(key: K, owner: Any? = null, action: () -> R): R {
        contract {
            callsInPlace(action, InvocationKind.AT_LEAST_ONCE)
        }
        return mutexes[key.hashCode().mod(mutexes.size)].withLock(owner, action)
    }

}