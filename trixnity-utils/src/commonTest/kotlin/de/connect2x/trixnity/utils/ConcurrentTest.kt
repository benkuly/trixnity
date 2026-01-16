package de.connect2x.trixnity.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.time.measureTime

class ConcurrentTest : TrixnityBaseTest() {
    @Test
    fun writeAndReadData() = runTest {
        val cut = concurrentMutableList<Int>()
        cut.write { add(1) }
        cut.write { add(2) }
        cut.read { toList() } shouldBe listOf(1, 2)
    }

    @Test
    fun throwExceptionsOnLeak() = runTest {
        val cut = concurrentMutableList<Int>()
        shouldThrow<IllegalArgumentException> {
            cut.write { this }
        }
        shouldThrow<IllegalArgumentException> {
            cut.read { this }
        }
    }

    @Test
    fun massiveReadWriteShouldNotThrowConcurrentModificationException() = runTest {
        val cut = concurrentMutableList<Int>()
        withContext(Dispatchers.Default) {
            val parallel = 10
            val writeOperations = 200
            val readOperations = writeOperations * 100
            val writeTime = async {
                val numbers = MutableStateFlow(0)
                measureTime {
                    coroutineScope {
                        repeat(parallel) {
                            launch {
                                repeat(writeOperations / parallel) {
                                    val number = numbers.getAndUpdate { it + 1 }
                                    cut.write { add(number) }
                                }
                            }
                        }
                    }
                }
            }
            val readTime = async {
                measureTime {
                    coroutineScope {
                        repeat(parallel) {
                            launch {
                                repeat(readOperations / parallel) {
                                    cut.read { toSet() }
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
            cut.read { toList() }.sorted() shouldBe List(writeOperations) { it }
        }
    }

    @Test
    fun getOrPut() = runTest {
        val cut = concurrentMutableMap<String, String>()
        val parallel = 10
        val writeOperations = 200
        val readOperations = writeOperations * 100

        cut.write { getOrPut("key1") { "value" } } shouldBe "value"
        cut.write { getOrPut("key2") { "value" } } shouldBe "value"

        repeat(parallel) {
            launch {
                repeat(writeOperations / parallel) {
                    cut.write { getOrPut("key1") { "value$it" } } shouldBe "value"
                    cut.write { getOrPut("key2") { "value$it" } } shouldBe "value"
                }
            }
            launch {
                repeat(readOperations / parallel) {
                    cut.read { get("key1") } shouldBe "value"
                    cut.read { get("key2") } shouldBe "value"
                }
            }
        }
    }
}