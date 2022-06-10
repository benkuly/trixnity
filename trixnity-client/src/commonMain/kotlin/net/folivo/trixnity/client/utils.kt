package net.folivo.trixnity.client

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.folivo.trixnity.api.client.retryOnRateLimit
import net.folivo.trixnity.client.crypto.IOlmEventService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

fun Event<*>?.getStateKey(): String? {
    return when (this) {
        is StateEvent -> this.stateKey
        is StrippedStateEvent -> this.stateKey
        else -> null
    }
}

fun Event<*>?.getEventId(): EventId? {
    return when (this) {
        is RoomEvent -> this.id
        else -> null
    }
}

fun Event<*>?.getOriginTimestamp(): Long? {
    return when (this) {
        is RoomEvent -> this.originTimestamp
        else -> null
    }
}

fun Event<*>?.getRoomId(): RoomId? {
    return when (this) {
        is RoomEvent -> this.roomId
        is StrippedStateEvent -> this.roomId
        is RoomAccountDataEvent -> this.roomId
        is EphemeralEvent -> this.roomId
        else -> null
    }
}

fun Event<*>?.getSender(): UserId? {
    return when (this) {
        is RoomEvent -> this.sender
        is StrippedStateEvent -> this.sender
        is ToDeviceEvent -> this.sender
        is EphemeralEvent -> this.sender
        else -> null
    }
}

fun String.toMxcUri(): Url =
    Url(this).also { require(it.protocol.name == "mxc") { "uri protocol was not mxc" } }

suspend fun possiblyEncryptEvent(
    content: MessageEventContent,
    roomId: RoomId,
    store: Store,
    olmEvent: IOlmEventService,
    user: IUserService
): MessageEventContent {
    return if (store.room.get(roomId).value?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
        user.loadMembers(roomId)
        store.room.get(roomId).first { it?.membersLoaded == true }

        val megolmSettings = store.roomState.getByStateKey<EncryptionEventContent>(roomId)?.content
        requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
        olmEvent.encryptMegolm(content, roomId, megolmSettings)
    } else content
}

@OptIn(ExperimentalTime::class)
suspend fun StateFlow<SyncState>.retryInfiniteWhenSyncIs(
    syncState: SyncState,
    vararg moreSyncStates: SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> Unit
) = coroutineScope {
    val syncStates = listOf(syncState) + moreSyncStates
    val shouldRun = map { syncStates.contains(it) }.stateIn(this)
    val schedule = Schedule.exponential<Throwable>(scheduleBase, scheduleFactor)
        .or(Schedule.spaced(scheduleLimit))
        .and(Schedule.doWhile { shouldRun.value }) // just stop, when we are not connected anymore
        .logInput {
            if (it is CancellationException) onCancel()
            else onError(it)
        }

    while (currentCoroutineContext().isActive) {
        shouldRun.first { it } // just wait, until we are connected again
        try {
            schedule.retry {
                retryOnRateLimit {
                    block()
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
        }
    }
}

@OptIn(ExperimentalTime::class)
suspend fun <T> retryWhen(
    condition: Flow<Boolean>,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): T = coroutineScope {
    val conditionState = MutableStateFlow(false)
    val job = launch { condition.collectLatest { conditionState.value = it } }
    val schedule = Schedule.exponential<Throwable>(scheduleBase, scheduleFactor)
        .or(Schedule.spaced(scheduleLimit))
        .and(Schedule.doWhile { conditionState.value }) // just stop, when it is false
        .logInput {
            if (it is CancellationException) onCancel()
            else onError(it)
        }

    flow {
        while (true) {
            condition.first { it } // just wait, until it is true again
            try {
                emit(
                    schedule.retry {
                        retryOnRateLimit {
                            block()
                        }
                    }
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
        }
    }.first().also { job.cancel() }
}

suspend fun <T> StateFlow<SyncState>.retryWhenSyncIs(
    syncState: SyncState,
    vararg moreSyncStates: SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): T = coroutineScope {
    val syncStates = listOf(syncState) + moreSyncStates
    retryWhen(
        condition = map { syncStates.contains(it) },
        scheduleBase = scheduleBase,
        scheduleFactor = scheduleFactor,
        scheduleLimit = scheduleLimit,
        onError = onError,
        onCancel = onCancel,
        block = block
    )
}