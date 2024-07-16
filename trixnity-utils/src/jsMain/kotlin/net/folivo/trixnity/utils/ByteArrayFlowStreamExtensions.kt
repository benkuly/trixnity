package net.folivo.trixnity.utils

import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.flow
import web.streams.ReadableStream
import web.streams.ReadableStreamReadDoneResult
import web.streams.ReadableStreamReadValueResult
import web.streams.WritableStream

fun byteArrayFlowFromReadableStream(streamFactory: suspend () -> ReadableStream<Uint8Array>) = flow {
    val reader = streamFactory().getReader()
    try {
        while (true) {
            when (val readResult = reader.read()) {
                is ReadableStreamReadValueResult -> {
                    emit(readResult.value.toByteArray())
                }

                is ReadableStreamReadDoneResult -> {
                    break
                }
            }
        }
    } finally {
        reader.cancel()
    }
}

suspend fun ByteArrayFlow.writeTo(writableStream: WritableStream<Uint8Array>) {
    val writer = writableStream.getWriter()
    try {
        collect {
            writer.write(it.toUint8Array())
        }
    } finally {
        writer.close()
    }
}

suspend fun WritableStream<Uint8Array>.write(content: ByteArrayFlow) = content.writeTo(this)