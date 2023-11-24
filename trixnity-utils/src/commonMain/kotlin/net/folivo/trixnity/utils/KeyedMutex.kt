package net.folivo.trixnity.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val log = KotlinLogging.logger {}

interface KeyedMutex<K> {
    data class ClaimedMutex(
        val claimCount: Int = 0,
        val mutex: Mutex = Mutex(),
    )

    suspend fun <T> withLock(key: K, block: suspend () -> T): T
}

class KeyedMutexImpl<K> : KeyedMutex<K> {

    private val mutexByKeyMutex = Mutex()
    private val mutexByKey = mutableMapOf<K, KeyedMutex.ClaimedMutex>()

    override suspend fun <T> withLock(key: K, block: suspend () -> T): T {
        val mutex = claimMutex(key)
        return try {
            mutex.withLock { block() }
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