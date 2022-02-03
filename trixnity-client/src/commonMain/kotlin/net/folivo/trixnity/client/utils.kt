package net.folivo.trixnity.client

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.retryOnRateLimit
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.UserService
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
        is MegolmEvent -> this.roomId
        is RoomAccountDataEvent -> this.roomId
        is EphemeralEvent -> this.roomId
        else -> null
    }
}

fun Event<*>?.getSender(): UserId? {
    return when (this) {
        is StateEvent -> this.sender
        is StrippedStateEvent -> this.sender
        is RoomEvent -> this.sender
        is ToDeviceEvent -> this.sender
        is EphemeralEvent -> this.sender
        is OlmEvent -> this.sender
        else -> null
    }
}

fun String.toMxcUri(): Url =
    Url(this).also { require(it.protocol.name == "mxc") { "uri protocol was not mxc" } }

suspend fun possiblyEncryptEvent(
    content: MessageEventContent,
    roomId: RoomId,
    store: Store,
    olm: OlmService,
    user: UserService
): MessageEventContent {
    return if (store.room.get(roomId).value?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
        user.loadMembers(roomId)
        withTimeout(30_000) {
            store.room.get(roomId).first { it?.membersLoaded == true }
        }

        val megolmSettings = store.roomState.getByStateKey<EncryptionEventContent>(roomId)?.content
        requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
        olm.events.encryptMegolm(content, roomId, megolmSettings)
    } else content
}

@OptIn(ExperimentalTime::class)
suspend fun StateFlow<SyncApiClient.SyncState>.retryInfiniteWhenSyncIs(
    syncState: SyncApiClient.SyncState,
    vararg moreSyncStates: SyncApiClient.SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    scope: CoroutineScope,
    block: suspend () -> Unit
) {
    val syncStates = listOf(syncState) + moreSyncStates
    val shouldRun = this.map { syncStates.contains(it) }.stateIn(scope)
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
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }
}

@OptIn(ExperimentalTime::class)
suspend fun <T> retryWhen(
    condition: StateFlow<Boolean>,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    block: suspend () -> T
): T {
    val schedule = Schedule.exponential<Throwable>(scheduleBase, scheduleFactor)
        .or(Schedule.spaced(scheduleLimit))
        .and(Schedule.doWhile { condition.value }) // just stop, when it is false
        .logInput {
            if (it is CancellationException) onCancel()
            else onError(it)
        }

    return flow {
        while (currentCoroutineContext().isActive) {
            condition.first { it } // just wait, until it is true again
            try {
                emit(
                    schedule.retry {
                        retryOnRateLimit {
                            block()
                        }
                    }
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
            }
        }
    }.first()
}

suspend fun <T> StateFlow<SyncApiClient.SyncState>.retryWhenSyncIs(
    syncState: SyncApiClient.SyncState,
    vararg moreSyncStates: SyncApiClient.SyncState,
    scheduleBase: Duration = 100.milliseconds,
    scheduleFactor: Double = 2.0,
    scheduleLimit: Duration = 5.minutes,
    onError: suspend (error: Throwable) -> Unit = {},
    onCancel: suspend () -> Unit = {},
    scope: CoroutineScope,
    block: suspend () -> T
): T {
    val syncStates = listOf(syncState) + moreSyncStates
    val condition = this.map { syncStates.contains(it) }.stateIn(scope)
    return retryWhen(
        condition = condition,
        scheduleBase = scheduleBase,
        scheduleFactor = scheduleFactor,
        scheduleLimit = scheduleLimit,
        onError = onError,
        onCancel = onCancel,
        block = block
    )
}