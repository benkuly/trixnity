package net.folivo.trixnity.vodozemac.megolm

import net.folivo.trixnity.vodozemac.UnpaddedBase64
import net.folivo.trixnity.vodozemac.VodozemacException
import net.folivo.trixnity.vodozemac.bindings.megolm.ExportedSessionKeyBindings
import net.folivo.trixnity.vodozemac.toByteArray
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.interopScope
import net.folivo.trixnity.vodozemac.utils.withResult

class ExportedSessionKey internal constructor(ptr: NativePointer) :
    Managed(ptr, ExportedSessionKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { ExportedSessionKeyBindings.toBytes(it, ptr) }

            ptr.toByteArray(size.intValue)
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {
        operator fun invoke(bytes: ByteArray): ExportedSessionKey = interopScope {
            val (tag, ptrOrErr, errSize) =
                withResult(NativePointerArray(3)) {
                    ExportedSessionKeyBindings.fromBytes(it, bytes.toInterop(), bytes.size)
                }

            if (tag.intValue != 0)
                throw VodozemacException(ptrOrErr.toByteArray(errSize.intValue).decodeToString())

            ExportedSessionKey(ptrOrErr)
        }

        operator fun invoke(base64: String): ExportedSessionKey =
            ExportedSessionKey(UnpaddedBase64.decode(base64))
    }
}
