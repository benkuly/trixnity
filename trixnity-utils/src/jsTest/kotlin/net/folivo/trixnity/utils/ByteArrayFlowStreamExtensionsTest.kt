package net.folivo.trixnity.utils

import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import js.buffer.ArrayBuffer
import js.objects.unsafeJso
import js.promise.Promise
import js.promise.invoke
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import web.streams.ReadableStream
import web.streams.UnderlyingDefaultSource
import web.streams.UnderlyingSink
import web.streams.WritableStream
import kotlin.test.Test

class ByteArrayFlowStreamExtensionsTest : TrixnityBaseTest() {
    @Test
    fun shouldCreateByteArrayFlow() = runTest {
        byteArrayFlowFromReadableStream {
            ReadableStream(unsafeJso<UnderlyingDefaultSource<Uint8Array<ArrayBuffer>>> {
                start = { controller ->
                    controller.enqueue("he".encodeToByteArray().toUint8Array())
                    controller.enqueue("llo".encodeToByteArray().toUint8Array())
                    controller.close()
                }
            })
        }.toList() shouldBe listOf(
            "he".toByteArray(),
            "llo".toByteArray()
        )
    }

    @Test
    fun shouldWriteFromByteArrayFlow() = runTest {
        val input = listOf("he".toByteArray(), "llo".toByteArray())
        val result = mutableListOf<ByteArray>()
        WritableStream(
            UnderlyingSink<Uint8Array<ArrayBuffer>>(
                write = { chunk, _ ->
                    Promise { resolve, _ ->
                        result.add(chunk.toByteArray())
                        resolve(null)
                    }
                }
            )).write(input.asFlow())
        result shouldBe input
    }
}