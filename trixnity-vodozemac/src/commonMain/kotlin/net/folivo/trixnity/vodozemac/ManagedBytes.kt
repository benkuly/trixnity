package net.folivo.trixnity.vodozemac

import net.folivo.trixnity.vodozemac.bindings.SliceBindings
import net.folivo.trixnity.vodozemac.utils.*

fun NativePointer.toByteArray(size: Int): ByteArray {
    return try {
        withResult(ByteArray(size)) { SliceBindings.copyNonoverlapping(this@toByteArray, it, size) }
    } finally {
        SliceBindings.dealloc(this, size, 1)
    }
}

fun NativePointer.toNativePointerArray(size: Int): NativePointerArray {
    return try {
        withResult(NativePointerArray(size)) {
            SliceBindings.copyNonoverlapping(this@toNativePointerArray, it, size * ptrSize)
        }
    } finally {
        SliceBindings.dealloc(this, size * ptrSize, ptrSize)
    }
}
