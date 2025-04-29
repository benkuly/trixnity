package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.store.cache.ObservableCacheStatistic")

data class ObservableCacheStatistic(
    val name: String,
    val all: Int,
    val subscribed: Int,
    val indexes: List<ObservableCacheIndexStatistic>
)

data class ObservableCacheIndexStatistic(
    val name: String,
    val all: Int,
    val subscribed: Int,
)

class ObservableCacheStatisticCollector : EventHandler {
    private val caches = mutableListOf<ObservableCache<*, *, *>>()

    internal fun addCache(cache: ObservableCache<*, *, *>) = caches.add(cache)

    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (log.isTraceEnabled())
            scope.launch {
                while (isActive) {
                    delay(10.seconds)
                    val statistics = caches.map { it.collectStatistic() }
                    val lowUsageStatistics = statistics.filter { it.all < 100 }
                    val midUsageStatistics = statistics.filter { it.all in 100..999 }
                    val highUsageStatistics = statistics.filter { it.all in 1_000..9_999 }
                    val extremeUsageStatistics = statistics.filter { it.all >= 10_000 }
                    log.trace {
                        """
                            cache statistic:
                                Low usage (< 100): ${lowUsageStatistics.map { it.name }}
                                Mid usage (> 100): ${midUsageStatistics.map { it.name }}
                                High usage (> 1_000): ${highUsageStatistics.map { it.name }}
                                Extreme usage (> 10_000): ${extremeUsageStatistics.map { it.name }}
                        """.trimIndent()
                    }
                    if (highUsageStatistics.isNotEmpty() || extremeUsageStatistics.isNotEmpty())
                        log.trace {
                            """
                            cache details:
                            ${highUsageStatistics.format()}
                            ${extremeUsageStatistics.format()}
                        """.trimIndent()
                        }
                }
            }
    }

    fun List<ObservableCacheStatistic>.format() = joinToString("\n") { statistic ->
        val indexNames = statistic.indexes.map { it.name }.joinToString("|") { padMax(it) }
        val indexAll = statistic.indexes.map { it.all }.joinToString("|") { padMax(it.toString()) }
        val indexSubscribed = statistic.indexes.map { it.all }.joinToString("|") { padMax(it.toString()) }
        """
            ------------------------------------------------------------
            Name:               ${statistic.name}
            All entries:        ${statistic.all}
            Subscribed entries: ${statistic.subscribed}
            Indexes:
                Name:               $indexNames
                All entries:        $indexAll
                Subscribed entries: $indexSubscribed
        """.trimIndent()
    }

    private fun padMax(input: String, length: Int = 16): String {
        return when {
            input.length > length -> input.substring(0, length)
            input.length < length -> input.padEnd(length)
            else -> input
        }
    }
}