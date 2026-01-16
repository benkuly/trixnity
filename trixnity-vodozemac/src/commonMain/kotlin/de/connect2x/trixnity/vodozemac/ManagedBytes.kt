package de.connect2x.trixnity.vodozemac

import de.connect2x.trixnity.vodozemac.bindings.SliceBindings
import de.connect2x.trixnity.vodozemac.utils.*

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
