package de.connect2x.trixnity.libolm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap


actual class OlmSessionPointer(val ptr: CPointer<cnames.structs.OlmSession>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}