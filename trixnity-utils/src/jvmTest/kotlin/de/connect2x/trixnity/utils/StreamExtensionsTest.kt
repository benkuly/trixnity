package de.connect2x.trixnity.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.milliseconds

class StreamExtensionsTest : TrixnityBaseTest() {

    @Test
    fun `OutputStream write » write content from ByteArrayFlow to OutputStream`() = runTest {
        val data = "hello".toByteArray()
        val byteArrayFlow = data.toByteArrayFlow()
        val outputStream = ByteArrayOutputStream()
        outputStream.write(byteArrayFlow)

        outputStream.toByteArray() contentEquals data

        outputStream.write(byteArrayFlow)

        outputStream.toByteArray() contentEquals (data + data)
    }

    @Test
    fun `byteArrayFlowFromInputStream » convert ByteArrayInputStream to ByteArrayFlow`() = runTest {
        val data = "hello".toByteArray()
        val byteArrayFlow = byteArrayFlowFromInputStream { data.inputStream() }
        assertContentEquals(data, byteArrayFlow.toByteArray())
    }

    @Test
    fun `byteArrayFlowFromInputStream » consume ByteArrayFlow twice`() = runTest {
        val data = "hello".toByteArray()
        val byteArrayFlow = byteArrayFlowFromInputStream { data.inputStream() }
        val first = byteArrayFlow.toByteArray()
        val second = byteArrayFlow.toByteArray()
        assertContentEquals(data, first)
        assertContentEquals(first, second)
    }

    @Test
    fun `byteArrayFlowFromInputStream » work with InputStreams where data is not available yet`() = runTest {
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
