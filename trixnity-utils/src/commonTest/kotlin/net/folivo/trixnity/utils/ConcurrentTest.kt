package net.folivo.trixnity.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

class ConcurrentTest {
    private lateinit var cut: Concurrent<List<Int>, MutableList<Int>>

    @BeforeTest
    fun before() {
        cut = concurrentOf { mutableListOf() }
    }

    @Test
    fun writeAndReadData() = runTest {
        cut.write { add(1) }
        cut.write { add(2) }
        cut.read { toList() } shouldBe listOf(1, 2)
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
                                cut.write { add(i) }
                            }
                        }
                    }
                }
            }
            val readTime = async {
                measureTime {
                    coroutineScope {
                        repeat(readOperations) {
                            launch {
                                cut.read { toSet() }
                            }
                        }
                    }
                }
            }
            println(
                "writeTime=${writeTime.await()} (${writeTime.await() / writeOperations}/operation), " +
                        "readTime=${readTime.await()} (${readTime.await() / readOperations}/operation)"
            )
            cut.read { toList() }.sorted() shouldBe List(writeOperations) { it }
        }
    }
}