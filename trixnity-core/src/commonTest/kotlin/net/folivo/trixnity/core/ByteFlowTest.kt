package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ByteFlowTest {
    private val helloBytes = "hello".toByteArray()

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
        helloBytes.toByteArrayFlow().toByteArray() shouldBe helloBytes
    }
}