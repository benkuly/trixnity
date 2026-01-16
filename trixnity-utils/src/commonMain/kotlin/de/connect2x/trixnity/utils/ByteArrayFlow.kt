package de.connect2x.trixnity.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList

typealias ByteArrayFlow = Flow<ByteArray>

const val BYTE_ARRAY_FLOW_CHUNK_SIZE: Long = 1_024 * 1_024 // 1 MiB

fun ByteArray.toByteArrayFlow(): ByteArrayFlow = flowOf(this)

suspend fun ByteArrayFlow.toByteArray(): ByteArray {
    val allByteArrays = toList()
    if (allByteArrays.size == 1) return allByteArrays.first()
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

/**
 * Returns null, when [maxSize] exceeded.
 */
suspend fun ByteArrayFlow.toByteArray(maxSize: Long): ByteArray? =
    try {
        limitedSize(maxSize).toByteArray()
    } catch (e: Exception) {
        if (e is MaxByteArrayFlowSizeException || e.cause is MaxByteArrayFlowSizeException) null
        else throw e
    }

private fun ByteArrayFlow.limitedSize(maxSize: Long): ByteArrayFlow = flow {
    var size = 0
    collect { nextBytes ->
        size += nextBytes.size
        if (size > maxSize) throw MaxByteArrayFlowSizeException()
        else emit(nextBytes)
    }
}

private class MaxByteArrayFlowSizeException : RuntimeException()