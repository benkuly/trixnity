package net.folivo.trixnity.vodozemac.megolm

import net.folivo.trixnity.vodozemac.UnpaddedBase64
import net.folivo.trixnity.vodozemac.VodozemacException
import net.folivo.trixnity.vodozemac.bindings.megolm.SessionKeyBindings
import net.folivo.trixnity.vodozemac.toByteArray
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.interopScope
import net.folivo.trixnity.vodozemac.utils.managedReachableScope

class SessionKey internal constructor(ptr: NativePointer) : Managed(ptr, SessionKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { SessionKeyBindings.toBytes(it, ptr) }

            ptr.toByteArray(size.intValue)
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {
        operator fun invoke(bytes: ByteArray): SessionKey = interopScope {
            val (tag, ptrOrErr, errSize) =
                withResult(NativePointerArray(3)) {
                    SessionKeyBindings.fromBytes(it, bytes.toInterop(), bytes.size)
                }

            if (tag.intValue != 0)
                throw VodozemacException(ptrOrErr.toByteArray(errSize.intValue).decodeToString())

            SessionKey(ptrOrErr)
        }

        operator fun invoke(base64: String): SessionKey = SessionKey(UnpaddedBase64.decode(base64))
    }
}
