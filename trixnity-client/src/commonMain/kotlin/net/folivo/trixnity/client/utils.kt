package net.folivo.trixnity.client

import arrow.fx.coroutines.Schedule
import arrow.fx.coroutines.retry
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.getByStateKey
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import kotlin.coroutines.cancellation.CancellationException
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
        // The UI should do that, when a room gets opened, because of lazy loading
        // members Trixnity may not know all devices for encryption yet.
        // To ensure an easy usage of Trixnity and because
        // the impact on performance is minimal, we call it here for prevention.
        user.loadMembers(roomId).getOrThrow()

        val megolmSettings = store.roomState.getByStateKey<EncryptionEventContent>(roomId)?.content
        requireNotNull(megolmSettings) { "room was marked as encrypted, but did not contain EncryptionEventContent in state" }
        olm.events.encryptMegolm(content, roomId, megolmSettings)
    } else content
}

@OptIn(ExperimentalTime::class)
suspend fun StateFlow<SyncApiClient.SyncState>.retryWhenSyncIsRunning(
    onError: suspend (error: Throwable) -> Unit,
    onCancel: suspend () -> Unit,
    scope: CoroutineScope,
    block: suspend () -> Unit
) {
    val isSyncRunning = this.map { it == SyncApiClient.SyncState.RUNNING }.stateIn(scope)
    val schedule = Schedule.exponential<Throwable>(100.milliseconds)
        .or(Schedule.spaced(5.minutes))
        .and(Schedule.doWhile { isSyncRunning.value }) // just stop, when we are not connected anymore
        .logInput {
            if (it is CancellationException) onCancel()
            else onError(it)
        }

    while (currentCoroutineContext().isActive) {
        isSyncRunning.first { it } // just wait, until we are connected again
        try {
            schedule.retry {
                block()
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
        }
    }
}