package net.folivo.trixnity.olm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap

actual class OlmPkSigningPointer(val ptr: CPointer<cnames.structs.OlmPkSigning>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}