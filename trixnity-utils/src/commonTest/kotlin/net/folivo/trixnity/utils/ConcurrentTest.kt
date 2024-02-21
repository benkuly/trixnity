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
            val writeOperations = 100
            val readOperations = writeOperations * 100
            val writeTime = async {
                measureTime {
                    coroutineScope {
                        repeat(writeOperations) { i ->
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
                        repeat(readOperations) { i ->
                            launch {
                                cut.read {
                                    toSet()
                                }
                            }
                        }
                    }
                }
            }
            println(
                "writeTime=${writeTime.await()} (${writeTime.await() / writeOperations}/operation), " +
                        "readTime=${readTime.await()} (${readTime.await() / readOperations}/operation)"
            )
        }
    }
}