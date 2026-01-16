package de.connect2x.trixnity.libolm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap


actual class OlmInboundGroupSessionPointer(val ptr: CPointer<cnames.structs.OlmInboundGroupSession>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}