package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_pk_signing
import net.folivo.trixnity.olm.OlmLibrary.pk_sign
import net.folivo.trixnity.olm.OlmLibrary.pk_signature_length
import net.folivo.trixnity.olm.OlmLibrary.pk_signing
import net.folivo.trixnity.olm.OlmLibrary.pk_signing_key_from_seed
import net.folivo.trixnity.olm.OlmLibrary.pk_signing_last_error
import net.folivo.trixnity.olm.OlmLibrary.pk_signing_public_key_length
import net.folivo.trixnity.olm.OlmLibrary.pk_signing_seed_length
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.encodeUnpaddedBase64

actual class OlmPkSigning private constructor(
    internal actual val ptr: OlmPkSigningPointer,
    actual val privateKey: String,
    actual val publicKey: String
) : WantsToBeFree {
    actual companion object {
        actual fun create(privateKey: String?): OlmPkSigning {
            val ptr = pk_signing()
            val publicKey = ByteArray(pk_signing_public_key_length().toInt())

            val result =
                privateKey?.let { pk_signing_key_from_seed(ptr, publicKey, it.decodeUnpaddedBase64Bytes()) to it }
                    ?: withRandom(pk_signing_seed_length()) { seed ->
                        requireNotNull(seed)
                        pk_signing_key_from_seed(ptr, publicKey, seed) to seed.encodeUnpaddedBase64()
                    }
            checkError(ptr, result.first, ::pk_signing_last_error)
            return OlmPkSigning(ptr, result.second, publicKey.decodeToString())
        }
    }

    actual override fun free() {
        clear_pk_signing(ptr)
        ptr.free()
    }

    actual fun sign(message: String): String {
        val signature = ByteArray(pk_signature_length().toInt())
        val size = checkResult {
            pk_sign(ptr, message.encodeToByteArray(), signature)
        }
        return signature.decodeToString(endIndex = size.toInt())
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), OlmLibrary::pk_signing_last_error)

}