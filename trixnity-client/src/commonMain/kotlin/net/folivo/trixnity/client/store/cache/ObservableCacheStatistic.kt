package net.folivo.trixnity.client.store.cache

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.EventHandler
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }

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
    private val caches = mutableListOf<ObservableCacheBase<*, *>>()

    internal fun addCache(cache: ObservableCacheBase<*, *>) = caches.add(cache)

    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (log.isDebugEnabled())
            scope.launch {
                while (isActive) {
                    delay(10.seconds)
                    val statistics = caches.map { it.collectStatistic() }
                    val lowUsageStatistics = statistics.filter { it.all < 100 }
                    val midUsageStatistics = statistics.filter { it.all in 100..999 }
                    val highUsageStatistics = statistics.filter { it.all in 1_000..9_999 }
                    val extremeUsageStatistics = statistics.filter { it.all >= 10_000 }
                    log.debug {
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

    fun List<ObservableCacheStatistic>.format() = map { statistic ->
        val indexNames = statistic.indexes.map { it.name }.joinToString("|") { "%-16s".format(it) }
        val indexAll = statistic.indexes.map { it.all }.joinToString("|") { "%-16s".format(it) }
        val indexSubscribed =
            statistic.indexes.map { it.all }.joinToString("|") { "%-16s".format(it) }
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
    }.joinToString("\n")
}