package net.folivo.trixnity.olm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap

actual class OlmPkEncryptionPointer(val ptr: CPointer<cnames.structs.OlmPkEncryption>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}