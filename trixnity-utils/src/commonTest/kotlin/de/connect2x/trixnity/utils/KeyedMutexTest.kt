package de.connect2x.trixnity.utils

import de.connect2x.lognity.api.logger.Level
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class KeyedMutexTest : TrixnityBaseTest() {

    override val packageLogLevels: Map<String, Level>
        get() = mapOf("de.connect2x.trixnity" to Level.INFO)

    @Test
    fun shouldLockKey() = runTest {
        val keyedMutex = KeyedMutex<String>()
        val locked = MutableStateFlow(0)
        val jobs = (1..1000).map {
            launch {
                keyedMutex.withLock("key") {
                    locked.value++
                    delay(Duration.INFINITE)
                }
            }
        }
        delay(1.seconds)
        jobs.forEach { it.cancel() }
        locked.value shouldBe 1
    }

    @Test
    fun shouldLockMultipleKeys() = runTest {
        val keyedMutex = KeyedMutex<String>()
        val locked = MutableStateFlow(0)
        val jobs = (1..1000).map { i ->
            launch {
                keyedMutex.withLock("key$i") {
                    locked.value++
                    delay(Duration.INFINITE)
                }
            }
        }
        delay(1.seconds)
        jobs.forEach { it.cancel() }
        locked.value shouldBe 1000
    }
}