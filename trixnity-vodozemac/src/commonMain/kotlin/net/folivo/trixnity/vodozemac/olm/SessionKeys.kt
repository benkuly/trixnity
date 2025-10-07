package net.folivo.trixnity.vodozemac.olm

import net.folivo.trixnity.vodozemac.Curve25519PublicKey
import net.folivo.trixnity.vodozemac.bindings.olm.SessionKeysBindings
import net.folivo.trixnity.vodozemac.toByteArray
import net.folivo.trixnity.vodozemac.utils.*

class SessionKeys internal constructor(ptr: NativePointer) :
    Managed(ptr, SessionKeysBindings::free) {

    val sessionId: String
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { SessionKeysBindings.sessionId(it, ptr) }

            ptr.toByteArray(size.intValue).decodeToString()
        }

    val identityKey: Curve25519PublicKey
        get() = managedReachableScope { Curve25519PublicKey(SessionKeysBindings.identityKey(ptr)) }

    val baseKey: Curve25519PublicKey
        get() = managedReachableScope { Curve25519PublicKey(SessionKeysBindings.baseKey(ptr)) }

    val oneTimeKey: Curve25519PublicKey
        get() = managedReachableScope { Curve25519PublicKey(SessionKeysBindings.oneTimeKey(ptr)) }
}
