package net.folivo.trixnity.olm

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType

actual class OlmSessionPointer : PointerType {
    constructor(address: Pointer?) : super(address)
    constructor() : super()

    actual fun free() {
        Native.free(Pointer.nativeValue(pointer))
    }
}