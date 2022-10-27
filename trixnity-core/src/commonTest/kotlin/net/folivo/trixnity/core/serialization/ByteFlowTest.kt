package net.folivo.trixnity.core.serialization

import io.kotest.matchers.shouldBe
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.core.toByteReadChannel
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ByteFlowTest {
    private val helloBytes = "hello".toByteArray()

    @Test
    fun shouldConvertByteReadChannelToByteFlow() = runTest {
        ByteReadChannel(helloBytes).toByteFlow().toList().toByteArray() shouldBe helloBytes
    }

    @Test
    fun shouldConvertByteFlowToReadChannel() = runTest {
        helloBytes.toByteFlow().toByteReadChannel().toByteArray() shouldBe helloBytes
    }

    @Test
    fun shouldConvertByteFlowToAndFromByteArray() = runTest {
        helloBytes.toByteFlow().toByteArray() shouldBe helloBytes
    }
}