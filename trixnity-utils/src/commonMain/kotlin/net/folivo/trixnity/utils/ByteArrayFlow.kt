package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.*

typealias ByteArrayFlow = Flow<ByteArray>

const val BYTE_ARRAY_FLOW_CHUNK_SIZE: Long = 1_024 * 1_024 // 1 MiB

fun ByteArray.toByteArrayFlow(): ByteArrayFlow = flowOf(this)

suspend fun ByteArrayFlow.toByteArray(): ByteArray {
    val allByteArrays = toList()
    val concatByteArray = ByteArray(allByteArrays.sumOf { it.size })
    var byteArrayPosition = 0
    allByteArrays.forEach { byteArray ->
        if (byteArray.isNotEmpty()) {
            byteArray.copyInto(concatByteArray, byteArrayPosition)
            byteArrayPosition += byteArray.size
        }
    }
    return concatByteArray
}

fun ByteArrayFlow.takeBytes(size: Int): ByteArrayFlow = flow {
    var currentSize = 0
    takeWhile { next ->
        currentSize += next.size
        currentSize <= size
    }.collect(this)
}