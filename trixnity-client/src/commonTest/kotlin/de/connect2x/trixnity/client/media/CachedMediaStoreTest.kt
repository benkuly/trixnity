package de.connect2x.trixnity.client.media

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.TestScope
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.testClock
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CachedMediaStoreTest : TrixnityBaseTest() {

    private val url = "url1"
    private val content = "testcontent".map { it.toString().toByteArray() }.asFlow()

    private val config = MatrixClientConfiguration(
        cacheExpireDurations = MatrixClientConfiguration.CacheExpireDurations.default(1.minutes).copy(
            media = 10.seconds
        )
    )

    private fun TestScope.cut() = InMemoryMediaStore(
        coroutineScope = backgroundScope,
        configuration = config,
        clock = testClock,
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
        val usageScope = usageScope()
        cut.addMedia(url, content)
        val media1 = cut.getMedia(url)?.toByteArray(usageScope)

        advanceCacheExpiration()

        val media2 = cut.getMedia(url)?.toByteArray()

        media1.shouldNotBeNull()
        media2.shouldNotBeNull()
        media1 shouldBeSameInstanceAs media2

        usageScope.coroutineContext.job.cancelAndJoin()
        advanceCacheExpiration()

        val media3 = cut.getMedia(url)?.toByteArray()

        media3.shouldNotBeNull()
        media1 shouldNotBeSameInstanceAs media3
    }

    private suspend fun advanceCacheExpiration() {
        delay(3.seconds)
        delay(13.seconds)
    }

    private fun TestScope.usageScope() = backgroundScope + SupervisorJob(backgroundScope.coroutineContext.job)
}
