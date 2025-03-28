package net.folivo.trixnity.olm

import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.encodeUnpaddedBase64

actual class OlmPkSigning private constructor(
    internal actual val ptr: OlmPkSigningPointer,
    actual val privateKey: String,
    actual val publicKey: String
) : WantsToBeFree {
    actual companion object {
        actual suspend fun create(privateKey: String?): OlmPkSigning {
            initOlm()
            val ptr: PkSigning = rethrow { js("new Olm.PkSigning()") }.unsafeCast<OlmPkSigningPointer>()
            val newPrivateKey = privateKey?.decodeUnpaddedBase64Bytes()?.toUint8Array() ?: ptr.generate_seed()
            val publicKey = rethrow { ptr.init_with_seed(newPrivateKey) }
            return OlmPkSigning(ptr, newPrivateKey.toByteArray().encodeUnpaddedBase64(), publicKey)
        }
    }

    actual override fun free() = ptr.free()

    actual fun sign(message: String): String = rethrow { ptr.sign(message) }
}