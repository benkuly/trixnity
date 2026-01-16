package de.connect2x.trixnity.vodozemac.sas

import de.connect2x.trixnity.vodozemac.UnpaddedBase64
import de.connect2x.trixnity.vodozemac.bindings.sas.MacBindings
import de.connect2x.trixnity.vodozemac.utils.*
import de.connect2x.trixnity.vodozemac.utils.interopScope
import de.connect2x.trixnity.vodozemac.utils.managedReachableScope
import de.connect2x.trixnity.vodozemac.utils.withResult

class Mac internal constructor(ptr: NativePointer) : Managed(ptr, MacBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope { withResult(ByteArray(32)) { MacBindings.asBytes(ptr, it) } }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {
        operator fun invoke(bytes: ByteArray): Mac = interopScope {
            require(bytes.size == 32)
            Mac(MacBindings.fromSlice(bytes.toInterop()))
        }

        operator fun invoke(base64: String): Mac = this(UnpaddedBase64.decode(base64))
    }
}
