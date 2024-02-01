package net.folivo.trixnity.utils

import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import js.objects.jso
import js.promise.Promise
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import web.streams.ReadableStream
import web.streams.UnderlyingDefaultSource
import web.streams.UnderlyingSink
import web.streams.WritableStream
import kotlin.test.Test

class ByteArrayFlowStreamExtensionsTest {
    @Test
    fun shouldCreateByteArrayFlow() = runTest {
        byteArrayFlowFromReadableStream {
            ReadableStream(jso<UnderlyingDefaultSource<Uint8Array>> {
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
        WritableStream(jso<UnderlyingSink<Uint8Array>> {
            write = { chunk, _ ->
                Promise { resolve, _ ->
                    result.add(chunk.toByteArray())
                    resolve(null)
                }
            }
        }).write(input.asFlow())
        result shouldBe input
    }
}