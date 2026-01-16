package de.connect2x.trixnity.libolm

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap

actual class OlmPkDecryptionPointer(val ptr: CPointer<cnames.structs.OlmPkDecryption>) {
    actual fun free() {
        nativeHeap.free(ptr)
    }
}