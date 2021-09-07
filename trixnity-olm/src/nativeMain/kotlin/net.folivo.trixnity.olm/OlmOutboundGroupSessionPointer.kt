package net.folivo.trixnity.olm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap


actual class OlmOutboundGroupSessionPointer(val ptr: CPointer<cnames.structs.OlmOutboundGroupSession>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}