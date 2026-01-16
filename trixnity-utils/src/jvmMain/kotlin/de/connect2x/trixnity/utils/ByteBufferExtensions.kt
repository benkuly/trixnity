package de.connect2x.trixnity.utils

import kotlinx.coroutines.flow.flowOf
import java.nio.ByteBuffer

fun ByteBuffer.toByteArrayFlow(): ByteArrayFlow {
    return flowOf(array().copyOf())
}