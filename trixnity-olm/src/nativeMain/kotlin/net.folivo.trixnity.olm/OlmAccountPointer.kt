package net.folivo.trixnity.olm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap


actual class OlmAccountPointer(val ptr: CPointer<cnames.structs.OlmAccount>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}