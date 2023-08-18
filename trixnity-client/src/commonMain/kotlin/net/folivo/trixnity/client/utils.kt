package net.folivo.trixnity.client

import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Deprecated(
    "use stateKeyOrNull instead",
    ReplaceWith("this.stateKeyOrNull", "net.folivo.trixnity.core.model.events.stateKeyOrNull")
)
fun Event<*>?.getStateKey(): String? = this?.stateKeyOrNull

@Deprecated(
    "use eventIdOrNull instead",
    ReplaceWith("this.eventIdOrNull", "net.folivo.trixnity.core.model.events.eventIdOrNull")
)
fun Event<*>?.getEventId(): EventId? = this?.eventIdOrNull

@Deprecated(
    "use originTimestampOrNull instead",
    ReplaceWith("this.originTimestampOrNull", "net.folivo.trixnity.core.model.events.originTimestampOrNull")
)
fun Event<*>?.getOriginTimestamp(): Long? = this?.originTimestampOrNull

@Deprecated(
    "use roomIdOrNull instead",
    ReplaceWith("this.roomIdOrNull", "net.folivo.trixnity.core.model.events.roomIdOrNull")
)
fun Event<*>?.getRoomId(): RoomId? = this?.roomIdOrNull

@Deprecated(
    "use senderOrNull instead",
    ReplaceWith("this.senderOrNull", "net.folivo.trixnity.core.model.events.senderOrNull")
)
fun Event<*>?.getSender(): UserId? = this?.senderOrNull

fun String.toMxcUri(): Url =
    Url(this).also { require(it.protocol.name == "mxc") { "uri protocol was not mxc" } }

/**
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <K, V> Flow<Map<K, Flow<V?>>?>.flatten(
    filterNullValues: Boolean = true,
    throttle: Duration = 200.milliseconds,
): Flow<Map<K, V?>?> =
    conflate()
        .transform {
            emit(it)
            delay(throttle)
        }
        .flatMapLatest { map ->
            if (map == null) flowOf(null)
            else if (map.isEmpty()) flowOf(emptyMap())
            else {
                val innerFlowsWithKey = map.map { entry -> entry.value.map { entry.key to it } }
                combine(innerFlowsWithKey) { innerFlowsWithKeyArray ->
                    innerFlowsWithKeyArray.toMap()
                        .let {
                            if (filterNullValues) it.filterValues { value -> value != null }
                            else it
                        }
                }
            }
        }