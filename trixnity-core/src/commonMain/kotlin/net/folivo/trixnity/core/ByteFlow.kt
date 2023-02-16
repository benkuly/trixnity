package net.folivo.trixnity.core

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*

typealias ByteArrayFlow = Flow<ByteArray>

const val BYTE_ARRAY_FLOW_CHUNK_SIZE: Long = 2_048 // 2 KB

fun ByteReadChannel.toByteArrayFlow(): ByteArrayFlow = flow {
    while (isClosedForRead.not()) {
        emit(readRemaining(BYTE_ARRAY_FLOW_CHUNK_SIZE).readBytes())
    }
}.onCompletion { if (it != null) this@toByteArrayFlow.cancel(it) }

@OptIn(DelicateCoroutinesApi::class)
suspend fun ByteArrayFlow.toByteReadChannel(): ByteReadChannel = GlobalScope.writer {
    writeTo(channel)
}.channel

suspend fun ByteArrayFlow.writeTo(byteWriteChannel: ByteWriteChannel) {
    try {
        collect { byteArray ->
            byteWriteChannel.writePacket {
                writeFully(byteArray)
            }
        }
        byteWriteChannel.close()
    } catch (exception: Exception) {
        byteWriteChannel.close(exception)
    }
}

fun ByteArray.toByteArrayFlow(): ByteArrayFlow = flowOf(this)

suspend fun ByteArrayFlow.toByteArray(): ByteArray = toList().reduce { old, new -> old + new }