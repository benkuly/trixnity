package net.folivo.trixnity.client.room

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.folivo.trixnity.core.model.RoomId

private val log = KotlinLogging.logger {}

class TimelineMutex {

    private val internalMutex = MutableStateFlow<Map<RoomId, Mutex>>(mapOf())

    suspend fun <T> withLock(roomId: RoomId, block: suspend () -> T): T =
        requireNotNull(internalMutex.updateAndGet { if (it.containsKey(roomId)) it else it + (roomId to Mutex()) }[roomId])
            .withLock {
                log.trace { "lock $roomId" }
                block()
                    .also { log.trace { "unlock $roomId" } }
            }
}