package de.connect2x.trixnity.utils

import io.kotest.matchers.shouldBe
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class ByteArrayFlowTest : TrixnityBaseTest() {
    private val helloBytes = "hello".toByteArray()
    private val helloBytesFlow = flowOf("he".toByteArray(), "llo".toByteArray())

    @Test
    fun shouldConvertByteReadChannelToByteFlow() = runTest {
        ByteReadChannel(helloBytes).toByteArrayFlow().toList().first() shouldBe helloBytes
    }

    @Test
    fun shouldConvertByteFlowToReadChannel() = runTest {
        helloBytes.toByteArrayFlow().toByteReadChannel().toByteArray() shouldBe helloBytes
    }

    @Test
    fun shouldConvertByteFlowToAndFromByteArray() = runTest {
        helloBytesFlow.toByteArray() shouldBe helloBytes
    }
}