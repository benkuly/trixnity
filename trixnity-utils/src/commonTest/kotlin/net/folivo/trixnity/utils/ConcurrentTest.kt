package net.folivo.trixnity.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

class ConcurrentTest {
    private lateinit var cut: Concurrent<List<String>, MutableList<String>>

    @BeforeTest
    fun before() {
        cut = concurrentOf { mutableListOf() }
    }

    @Test
    fun writeAndReadData() = runTest {
        cut.write { add("a") }
        cut.write { add("b") }
        cut.read { toList() } shouldBe listOf("a", "b")
    }

    @Test
    fun throwExceptionsOnLeak() = runTest {
        shouldThrow<IllegalArgumentException> {
            cut.write { this }
        }
        shouldThrow<IllegalArgumentException> {
            cut.read { this }
        }
    }

    @Test
    fun massiveReadWriteShouldNotThrowConcurrentModificationException() = runTest {
        withContext(Dispatchers.Default) {
            val operations = 1_000
            val writeTime = async {
                measureTime {
                    coroutineScope {
                        repeat(operations) { i ->
                            launch {
                                cut.write { add("$i") }
                            }
                        }
                    }
                }
            }
            val readTime = async {
                measureTime {
                    coroutineScope {
                        repeat(operations) {
                            launch {
                                cut.read { getOrNull(0) }
                            }
                        }
                    }
                }
            }
            println(
                "writeTime=${writeTime.await()} (${writeTime.await() / operations}/operation), " +
                        "readTime=${readTime.await()} (${readTime.await() / operations}/operation)"
            )
        }
    }
}