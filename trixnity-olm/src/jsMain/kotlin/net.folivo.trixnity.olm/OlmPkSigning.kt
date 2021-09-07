package net.folivo.trixnity.olm

import io.ktor.util.*
import org.khronos.webgl.Uint8Array
import rethrow

actual class OlmPkSigning private constructor(
    internal actual val ptr: OlmPkSigningPointer,
    actual val privateKey: String,
    actual val publicKey: String
) : WantsToBeFree {
    actual companion object {
        @OptIn(InternalAPI::class)
        actual fun create(privateKey: String?): OlmPkSigning {
            val ptr: PkSigning = rethrow { js("new Olm.PkSigning()") }.unsafeCast<OlmPkSigningPointer>()
            val privateKey = privateKey?.decodeUnpaddedBase64Bytes()?.unsafeCast<Uint8Array>() ?: ptr.generate_seed()
            val publicKey = rethrow { ptr.init_with_seed(privateKey) }
            return OlmPkSigning(ptr, privateKey.unsafeCast<ByteArray>().encodeUnpaddedBase64(), publicKey)
        }
    }

    actual override fun free() = ptr.free()

    actual fun sign(message: String): String = rethrow { ptr.sign(message) }
}