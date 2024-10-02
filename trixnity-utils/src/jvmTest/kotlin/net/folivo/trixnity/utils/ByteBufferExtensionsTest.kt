package net.folivo.trixnity.utils

import io.kotest.core.spec.style.ShouldSpec
import java.nio.ByteBuffer
import kotlin.test.assertContentEquals

class ByteBufferExtensionsTest : ShouldSpec() {
    init {
        context("ByteBuffer.toByteArrayFlow") {
            should("convert ByteBuffer to ByteArrayFlow") {
                val data = "hello".toByteArray()
                val byteArrayFlow = ByteBuffer.wrap(data).toByteArrayFlow()
                assertContentEquals(data, byteArrayFlow.toByteArray())
            }
        }
    }
}
