package net.folivo.trixnity.client.media

import io.github.oshai.kotlinlogging.Level
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.ClockMock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.test.utils.LoggedTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CachedMediaStoreTest : LoggedTest {
    override val defaultLogLevel: Level = Level.TRACE
    private lateinit var clock: ClockMock
    private lateinit var config: MatrixClientConfiguration

    private val url = "url1"
    private val content = "testcontent".map { it.toString().toByteArray() }.asFlow()

    @BeforeTest
    fun beforeTest() {
        clock = ClockMock()
        config = MatrixClientConfiguration(
            cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(1.minutes).copy(
                media = 10.seconds
            )
        )
    }

    private fun TestScope.cut() = InMemoryCachedMediaStore(
        coroutineScope = backgroundScope,
        configuration = config,
        clock = clock
    )

    @Test
    fun shouldCacheMedia() = runTest {
        val cut = cut()
        cut.addMedia(url, content)
        val media1 = cut.getMedia(url)?.toByteArray(backgroundScope)
        val media2 = cut.getMedia(url)?.toByteArray(backgroundScope)

        media1.shouldNotBeNull()
        media2.shouldNotBeNull()
        media1 shouldBeSameInstanceAs media2
    }

    @Test
    fun shouldRespectSizeLimitOfBytes() = runTest {
        val cut = cut()
        cut.addMedia(url, content)
        val media = cut.getMedia(url)

        media.shouldNotBeNull()
        media.toByteArray(maxSize = 5)?.decodeToString() shouldBe null
        media.toByteArray(maxSize = 20)?.decodeToString() shouldBe "testcontent"
    }

    @Test
    fun shouldRespectSizeLimitOfExpected() = runTest {
        val cut = cut()
        cut.addMedia(url, content)
        val media = cut.getMedia(url)

        media.shouldNotBeNull()
        media.toByteArray(expectedSize = 6, maxSize = 5)?.decodeToString() shouldBe null
        media.toByteArray(expectedSize = 20, maxSize = 20)?.decodeToString() shouldBe "testcontent"
    }

    @Test
    fun shouldExpireCacheAfterDelay() = runTest {
        val cut = cut()
        cut.addMedia(url, content)
        val media1 = cut.getMedia(url)?.toByteArray()

        advanceCacheExpiration()

        val media2 = cut.getMedia(url)?.toByteArray()

        media1.shouldNotBeNull()
        media2.shouldNotBeNull()
        media1 shouldNotBeSameInstanceAs media2
    }

    @Test
    fun shouldExpireCacheWhenNotUsed() = runTest {
        val cut = cut()
        val usageScope = CoroutineScope(Dispatchers.Default)
        cut.addMedia(url, content)
        val media1 = cut.getMedia(url)?.toByteArray(usageScope)

        advanceCacheExpiration()

        val media2 = cut.getMedia(url)?.toByteArray()

        media1.shouldNotBeNull()
        media2.shouldNotBeNull()
        media1 shouldBeSameInstanceAs media2

        usageScope.cancel()
        usageScope.coroutineContext.job.join()
        advanceCacheExpiration()

        val media3 = cut.getMedia(url)?.toByteArray()

        media3.shouldNotBeNull()
        media1 shouldNotBeSameInstanceAs media3
    }

    private suspend fun advanceCacheExpiration() {
        delay(3.seconds)
        clock.nowValue = clock.nowValue.plus(11.seconds)
        delay(13.seconds)
    }
}
