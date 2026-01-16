package de.connect2x.trixnity.libolm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap


actual class OlmUtilityPointer(val ptr: CPointer<cnames.structs.OlmUtility>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}