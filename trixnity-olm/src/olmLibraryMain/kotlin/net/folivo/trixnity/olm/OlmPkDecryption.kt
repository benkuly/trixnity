package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_pk_decryption
import net.folivo.trixnity.olm.OlmLibrary.pickle_pk_decryption
import net.folivo.trixnity.olm.OlmLibrary.pickle_pk_decryption_length
import net.folivo.trixnity.olm.OlmLibrary.pk_decrypt
import net.folivo.trixnity.olm.OlmLibrary.pk_decryption
import net.folivo.trixnity.olm.OlmLibrary.pk_decryption_last_error
import net.folivo.trixnity.olm.OlmLibrary.pk_get_private_key
import net.folivo.trixnity.olm.OlmLibrary.pk_key_from_private
import net.folivo.trixnity.olm.OlmLibrary.pk_key_length
import net.folivo.trixnity.olm.OlmLibrary.pk_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibrary.pk_private_key_length
import net.folivo.trixnity.olm.OlmLibrary.unpickle_pk_decryption

actual class OlmPkDecryption private constructor(
    internal actual val ptr: OlmPkDecryptionPointer,
    actual val publicKey: String
) : WantsToBeFree {
    actual companion object {
        actual fun create(privateKey: String?): OlmPkDecryption {
            val privateKeyLength = pk_private_key_length()
            val publicKey = ByteArray(pk_key_length().toInt())

            val ptr = pk_decryption()
            try {
                if (privateKey == null) {
                    withRandom(privateKeyLength) { randomPrivateKey ->
                        requireNotNull(randomPrivateKey)
                        val result = pk_key_from_private(ptr, publicKey, randomPrivateKey)
                        checkError(ptr, result, ::pk_decryption_last_error)
                    }
                } else {
                    val result = pk_key_from_private(ptr, publicKey, privateKey.decodeUnpaddedBase64Bytes())
                    checkError(ptr, result, ::pk_decryption_last_error)
                }
            } catch (e: Exception) {
                clear_pk_decryption(ptr)
                throw e
            }
            return OlmPkDecryption(ptr, publicKey.decodeToString())
        }

        actual fun unpickle(key: String, pickle: String): OlmPkDecryption {
            val ptr = pk_decryption()
            val publicKey = ByteArray(pk_key_length().toInt())
            val result = unpickle_pk_decryption(ptr, key.encodeToByteArray(), pickle.encodeToByteArray(), publicKey)
            checkError(ptr, result, ::pk_decryption_last_error)
            return OlmPkDecryption(ptr, publicKey.decodeToString())
        }
    }

    actual val privateKey: String
        get() {
            val privateKey = ByteArray(pk_private_key_length().toInt())
            checkResult { pk_get_private_key(ptr, privateKey) }
            return privateKey.encodeUnpaddedBase64()
        }

    actual override fun free() {
        clear_pk_decryption(ptr)
        ptr.free()
    }

    actual fun pickle(key: String): String = pickle(
        ptr,
        key,
        ::pickle_pk_decryption_length,
        ::pickle_pk_decryption,
        ::pk_decryption_last_error
    )

    actual fun decrypt(message: OlmPkMessage): String {
        val cipherTextBytes = message.cipherText.encodeToByteArray()
        val maxPlaintextLength = pk_max_plaintext_length(ptr, cipherTextBytes.size.toULong()).toInt()
        val plainText = ByteArray(maxPlaintextLength)

        val plainTextLength = checkResult {
            pk_decrypt(
                ptr,
                message.ephemeralKey.encodeToByteArray(),
                message.mac.encodeToByteArray(),
                cipherTextBytes,
                plainText
            )
        }

        return plainText.decodeToString(endIndex = plainTextLength.toInt())
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::pk_decryption_last_error)
}