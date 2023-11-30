package net.folivo.trixnity.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val log = KotlinLogging.logger {}

interface KeyedMutex<K> {
    data class ClaimedMutex(
        val claimCount: Int = 0,
        val mutex: Mutex = Mutex(),
    )

    suspend fun <T : Any?> withLock(key: K, block: suspend () -> T): T
}

class KeyedMutexImpl<K : Any> : KeyedMutex<K> {

    private val mutexByKeyMutex = Mutex()
    private val mutexByKey = mutableMapOf<K, KeyedMutex.ClaimedMutex>()

    private class Lock(
        val keyValue: Any,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lock>
    }

    override suspend fun <T : Any?> withLock(key: K, block: suspend () -> T): T =
        if (coroutineContext[Lock]?.keyValue == key) {
            block()
        } else {
            val mutex = claimMutex(key)
            try {
                mutex.withLock {
                    withContext(Lock(key)) {
                        block()
                    }
                }
            } finally {
                releaseMutex(key)
            }
        }


    private suspend fun claimMutex(key: K): Mutex = mutexByKeyMutex.withLock {
        log.trace { "claim mutex (key=$key)" }
        val claimedMutex = mutexByKey[key]
            ?.run { copy(claimCount = claimCount + 1) }
            ?: KeyedMutex.ClaimedMutex(1)
        mutexByKey[key] = claimedMutex
        claimedMutex.mutex
    }

    private suspend fun releaseMutex(key: K): Unit = mutexByKeyMutex.withLock {
        log.trace { "release mutex (key=$key)" }
        val claimedMutex = requireNotNull(mutexByKey[key])
        if (claimedMutex.claimCount == 1) mutexByKey.remove(key)
        else mutexByKey[key] = claimedMutex.copy(claimCount = claimedMutex.claimCount - 1)
    }
}