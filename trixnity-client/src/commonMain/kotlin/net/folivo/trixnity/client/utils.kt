package net.folivo.trixnity.client

import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.previousRoomId
import net.folivo.trixnity.core.model.RoomId
import org.koin.core.module.Module
import kotlin.Result.Companion.failure
import kotlin.jvm.JvmName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias ModuleFactory = () -> Module

fun String.toMxcUri(): Url =
    Url(this).also { require(it.protocol.name == "mxc") { "uri protocol was not mxc" } }

/**
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <K, V> Flow<Map<K, Flow<V?>>>.flatten(
    throttle: Duration = 200.milliseconds,
): Flow<Map<K, V?>> =
    transform {
        emit(it)
        delay(throttle)
    }.flatMapLatest { map ->
        if (map.isEmpty()) flowOf(emptyMap())
        else {
            val innerFlowsWithKey = map.map { entry -> entry.value.map { entry.key to it } }
            combine(innerFlowsWithKey) { innerFlowsWithKeyArray ->
                innerFlowsWithKeyArray.toMap()
            }
        }
    }.conflate()

/**
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <K, V> Flow<Map<K, Flow<V?>>>.flattenNotNull(
    throttle: Duration = 200.milliseconds,
): Flow<Map<K, V>> =
    transform {
        emit(it)
        delay(throttle)
    }.flatMapLatest { map ->
        if (map.isEmpty()) flowOf(emptyMap())
        else {
            val innerFlowsWithKey =
                map.map { entry -> entry.value.map { if (it == null) null else entry.key to it } }
            combine(innerFlowsWithKey) { innerFlowsWithKeyArray ->
                innerFlowsWithKeyArray.filterNotNull().toMap()
            }
        }
    }.conflate()

/**
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <K, reified V> Flow<Map<K, Flow<V?>>>.flattenValues(
    throttle: Duration = 200.milliseconds,
): Flow<List<V>> =
    transform {
        emit(it)
        delay(throttle)
    }.flatMapLatest { map ->
        if (map.isEmpty()) flowOf(listOf())
        else combine(map.values) { transform -> transform.filterNotNull() }
    }.conflate()

/**
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@JvmName("flattenList")
@OptIn(ExperimentalCoroutinesApi::class)
inline fun <reified V> Flow<List<Flow<V?>>>.flatten(
    throttle: Duration = 200.milliseconds,
): Flow<List<V>> =
    transform {
        emit(it)
        delay(throttle)
    }.flatMapLatest { map ->
        if (map.isEmpty()) flowOf(listOf())
        else combine(map) { transform -> transform.filterNotNull() }
    }.conflate()

/**
 * This collects all rooms, so when one changes, a new set gets emitted.
 * A change of the outer flow results in new collect of the inner flows. Because this is an expensive operation,
 * the outer flow is throttled by default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<Map<RoomId, Flow<Room?>>>.flattenValues(
    throttle: Duration = 200.milliseconds,
    filterUpgradedRooms: Boolean = true,
): Flow<Set<Room>> =
    transform {
        emit(it)
        delay(throttle)
    }.flatMapLatest {
        if (it.isEmpty()) flowOf(listOf())
        else combine(it.values) { transform -> transform.filterNotNull() }
    }.conflate().map { rooms ->
        rooms.filter { room ->
            if (filterUpgradedRooms) {
                val foundReplacementRoom =
                    room.nextRoomId?.let { nextRoomId ->
                        rooms.any {
                            it.roomId == nextRoomId
                                    && it.previousRoomId == room.roomId
                        }
                    } == true
                foundReplacementRoom.not()
            } else true
        }.toSet()
    }

internal inline fun <A, B> Result<A>.flatMap(transform: (value: A) -> Result<B>): Result<B> {
    return map(transform).fold({ it }, ::failure)
}