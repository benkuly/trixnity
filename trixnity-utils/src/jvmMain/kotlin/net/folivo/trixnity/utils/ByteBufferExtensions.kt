package net.folivo.trixnity.utils

import kotlinx.coroutines.flow.flowOf
import java.nio.ByteBuffer

fun ByteBuffer.toByteArrayFlow(): ByteArrayFlow {
    val buffer = ByteArray(remaining())
    get(buffer)
    return flowOf(buffer)
}