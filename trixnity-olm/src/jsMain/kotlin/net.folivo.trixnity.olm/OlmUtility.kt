package net.folivo.trixnity.olm

import js.typedarrays.toUint8Array

actual class OlmUtility private constructor() : WantsToBeFree {
    internal actual val ptr: OlmUtilityPointer = rethrow { js("new Olm.Utility()") }.unsafeCast<OlmUtilityPointer>()

    actual companion object {
        actual suspend fun create(): OlmUtility {
            initOlm()
            return OlmUtility()
        }
    }

    actual override fun free() = ptr.free()

    actual fun sha256(input: ByteArray): String = rethrow { ptr.sha256(input.toUint8Array()) }
    actual fun sha256(input: String): String = rethrow { ptr.sha256(input) }
    actual fun verifyEd25519(key: String, message: String, signature: String) =
        rethrow { ptr.ed25519_verify(key, message, signature) }
}