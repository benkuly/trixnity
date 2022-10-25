package net.folivo.trixnity.core

import io.ktor.utils.io.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList

typealias ByteFlow = Flow<Byte>

fun ByteReadChannel.toByteFlow(): ByteFlow = flow {
    while (isClosedForRead.not()) emit(readByte())
}.onCompletion { if (it != null) this@toByteFlow.cancel(it) }

@OptIn(DelicateCoroutinesApi::class)
suspend fun ByteFlow.toByteReadChannel(): ByteReadChannel {
    return GlobalScope.writer {
        writeTo(channel)
    }.channel
}

suspend fun ByteFlow.writeTo(byteWriteChannel: ByteWriteChannel) {
    try {
        collect {
            byteWriteChannel.writeByte(it)
        }
        byteWriteChannel.close()
    } catch (exception: Exception) {
        byteWriteChannel.close(exception)
    }
}

fun ByteArray.toByteFlow(): ByteFlow = flow {
    forEach { emit(it) }
}

suspend fun ByteFlow.toByteArray(): ByteArray = toList().toByteArray()