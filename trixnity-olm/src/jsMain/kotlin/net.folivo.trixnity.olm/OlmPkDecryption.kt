package net.folivo.trixnity.olm

import io.ktor.util.*
import org.khronos.webgl.Uint8Array
import rethrow

actual class OlmPkDecryption private constructor(
    internal actual val ptr: OlmPkDecryptionPointer,
    actual val publicKey: String
) : WantsToBeFree {
    actual companion object {
        @OptIn(InternalAPI::class)
        actual fun create(privateKey: String?): OlmPkDecryption {
            val ptr: PkDecryption = rethrow { js("new Olm.PkDecryption()") }.unsafeCast<OlmPkDecryptionPointer>()
            val publicKey = rethrow {
                privateKey?.let { ptr.init_with_private_key(it.decodeUnpaddedBase64Bytes().unsafeCast<Uint8Array>()) }
                    ?: ptr.generate_key()
            }
            return OlmPkDecryption(ptr, publicKey)
        }

        actual fun unpickle(key: String, pickle: String): OlmPkDecryption {
            val ptr: PkDecryption = rethrow { js("new Olm.PkDecryption()") }.unsafeCast<OlmPkDecryptionPointer>()
            return OlmPkDecryption(ptr, rethrow { ptr.unpickle(key, pickle) })
        }
    }

    @OptIn(InternalAPI::class)
    actual val privateKey: String = rethrow { ptr.get_private_key() }.unsafeCast<ByteArray>().encodeUnpaddedBase64()

    actual override fun free() = ptr.free()

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun decrypt(message: OlmPkMessage): String =
        rethrow { ptr.decrypt(message.ephemeralKey, message.mac, message.cipherText) }
}