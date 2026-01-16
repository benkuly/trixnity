package de.connect2x.trixnity.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val log = KotlinLogging.logger("de.connect2x.trixnity.utils.KeyedMutex")

open class KeyedMutex<K : Any> {
    private data class ClaimedMutex(
        val claimCount: Int = 0,
        val mutex: Mutex = Mutex(),
    )

    private val mutexByKeyMutex = Mutex()
    private val mutexByKey = mutableMapOf<K, ClaimedMutex>()

    suspend fun <T : Any?> withLock(key: K, block: suspend () -> T): T {
        return try {
            val mutex = claimMutex(key)
            mutex.withLock {
                block()
            }
        } finally {
            withContext(NonCancellable) {
                releaseMutex(key)
            }
        }
    }


    private suspend fun claimMutex(key: K): Mutex = mutexByKeyMutex.withLock {
        log.trace { "claim mutex (key=$key)" }
        val claimedMutex = mutexByKey[key]
            ?.run { copy(claimCount = claimCount + 1) }
            ?: ClaimedMutex(1)
        mutexByKey[key] = claimedMutex
        claimedMutex.mutex
    }

    private suspend fun releaseMutex(key: K): Unit = mutexByKeyMutex.withLock {
        log.trace { "release mutex (key=$key)" }
        val claimedMutex = mutexByKey[key] ?: return@withLock
        if (claimedMutex.claimCount == 1) mutexByKey.remove(key)
        else mutexByKey[key] = claimedMutex.copy(claimCount = claimedMutex.claimCount - 1)
    }
}