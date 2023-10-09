package net.folivo.trixnity.client

import io.ktor.http.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.model.events.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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