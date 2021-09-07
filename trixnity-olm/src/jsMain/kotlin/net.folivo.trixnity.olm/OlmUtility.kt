package net.folivo.trixnity.olm

import rethrow

actual class OlmUtility private constructor() : WantsToBeFree {
    internal actual val ptr: OlmUtilityPointer = rethrow { js("new Olm.Utility()") }.unsafeCast<OlmUtilityPointer>()

    actual companion object {
        actual fun create(): OlmUtility = OlmUtility()
    }

    actual override fun free() = ptr.free()

    actual fun sha256(input: String): String = rethrow { ptr.sha256(input) }
    actual fun verifyEd25519(key: String, message: String, signature: String) =
        rethrow { ptr.ed25519_verify(key, message, signature) }
}