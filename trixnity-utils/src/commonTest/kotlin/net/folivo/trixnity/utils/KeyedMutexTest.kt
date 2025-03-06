package net.folivo.trixnity.utils

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class KeyedMutexTest {
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
        advanceUntilIdle()
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
        advanceUntilIdle()
        jobs.forEach { it.cancel() }
        locked.value shouldBe 1000
    }
}