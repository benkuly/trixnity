package de.connect2x.trixnity.utils

import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ByteBufferExtensionsTest {

    @Test
    fun `ByteBuffer toByteArrayFlow » convert ByteBuffer to ByteArrayFlow`() = runTest {
        val data = "hello".toByteArray()
        val byteArrayFlow = ByteBuffer.wrap(data).toByteArrayFlow()
        assertContentEquals(data, byteArrayFlow.toByteArray())
    }

    @Test
    fun `ByteBuffer toByteArrayFlow » be consumable multiple times`() = runTest {
        val data = "hello".toByteArray()
        val byteArrayFlow = ByteBuffer.wrap(data).toByteArrayFlow()
        assertContentEquals(data, byteArrayFlow.toByteArray())
        assertContentEquals(data, byteArrayFlow.toByteArray())
    }

}
