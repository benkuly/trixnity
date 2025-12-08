package net.folivo.trixnity.olm

import kotlinx.cinterop.*
import platform.posix.size_t

internal inline fun <T : CPointed> genericInit(
    init: (CValuesRef<*>?) -> CPointer<T>?,
    size: size_t
): CPointer<T> {
    val memory = nativeHeap.allocArray<ByteVar>(size.convert())
    try {
        val ptr = init(memory)
        return checkNotNull(ptr)
    } catch (e: Exception) {
        nativeHeap.free(memory)
        throw OlmLibraryException(message = "could not init object", cause = e)
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.usize(): ULong = this.size.toULong()