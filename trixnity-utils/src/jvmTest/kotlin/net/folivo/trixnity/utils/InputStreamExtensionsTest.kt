package net.folivo.trixnity.utils

import io.kotest.core.spec.style.ShouldSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.milliseconds

class InputStreamExtensionsTest : ShouldSpec() {
    init {
        context("byteArrayFlowFromInputStream") {
            should("convert ByteArrayInputStream to ByteArrayFlow") {
                val data = "hello".toByteArray()
                val byteArrayFlow = byteArrayFlowFromInputStream { data.inputStream() }
                assertContentEquals(data, byteArrayFlow.toByteArray())
            }
            should("consume ByteArrayFlow twice") {
                val data = "hello".toByteArray()
                val byteArrayFlow = byteArrayFlowFromInputStream { data.inputStream() }
                val first = byteArrayFlow.toByteArray()
                val second = byteArrayFlow.toByteArray()
                assertContentEquals(data, first)
                assertContentEquals(first, second)
            }
            should("work with InputStreams where data is not available yet") {
                val outputStream = PipedOutputStream()
                var inputStream: InputStream? = PipedInputStream(outputStream)

                val byteArrayFlow = byteArrayFlowFromInputStream {
                    inputStream?.let {
                        inputStream = null
                        it
                    } ?: throw Exception("this stream cannot be subscribed twice")
                }

                val write = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    delay(10.milliseconds)
                    outputStream.write("he".toByteArray())
                    delay(10.milliseconds)
                    outputStream.write("llo".toByteArray())
                    delay(10.milliseconds)
                    outputStream.close()
                }

                val read = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    assertContentEquals("hello".toByteArray(), byteArrayFlow.toByteArray())
                }

                awaitAll(read, write)
            }
        }
    }
}
