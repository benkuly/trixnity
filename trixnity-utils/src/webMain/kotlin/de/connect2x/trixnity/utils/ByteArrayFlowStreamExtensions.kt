package de.connect2x.trixnity.utils

import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.flow
import web.streams.*

fun byteArrayFlowFromReadableStream(streamFactory: suspend () -> ReadableStream<Uint8Array<ArrayBuffer>>): ByteArrayFlow =
    flow {
        val reader = streamFactory().getReader()
        try {
            while (true) {
                val readResult = reader.read()
                if (readResult.done) break
                val readResultValue = readResult.value ?: break
                emit(readResultValue.toByteArray())
            }
        } finally {
            reader.cancel()
        }
    }

suspend fun ByteArrayFlow.writeTo(writableStream: WritableStream<Uint8Array<ArrayBuffer>>) {
    val writer = writableStream.getWriter()
    try {
        collect {
            writer.write(it.toUint8Array())
        }
    } finally {
        writer.close()
    }
}

suspend fun WritableStream<Uint8Array<ArrayBuffer>>.write(content: ByteArrayFlow) = content.writeTo(this)