package de.connect2x.trixnity.libolm

import de.connect2x.trixnity.libolm.OlmLibrary.clear_pk_encryption
import de.connect2x.trixnity.libolm.OlmLibrary.pk_ciphertext_length
import de.connect2x.trixnity.libolm.OlmLibrary.pk_encrypt
import de.connect2x.trixnity.libolm.OlmLibrary.pk_encrypt_random_length
import de.connect2x.trixnity.libolm.OlmLibrary.pk_encryption
import de.connect2x.trixnity.libolm.OlmLibrary.pk_encryption_last_error
import de.connect2x.trixnity.libolm.OlmLibrary.pk_encryption_set_recipient_key
import de.connect2x.trixnity.libolm.OlmLibrary.pk_key_length
import de.connect2x.trixnity.libolm.OlmLibrary.pk_mac_length

actual class OlmPkEncryption private constructor() : WantsToBeFree {
    internal actual val ptr: OlmPkEncryptionPointer = pk_encryption()

    actual companion object {
        actual fun create(recipientKey: String): OlmPkEncryption = OlmPkEncryption().apply {
            try {
                val result = pk_encryption_set_recipient_key(ptr, recipientKey.encodeToByteArray())
                checkError(ptr, result, ::pk_encryption_last_error)
            } catch (e: Exception) {
                free()
                throw e
            }
        }
    }

    actual override fun free() {
        clear_pk_encryption(ptr)
        ptr.free()
    }

    actual fun encrypt(plainText: String): OlmPkMessage {
        val plainTextBytes = plainText.encodeToByteArray()
        val cipherTextLength = pk_ciphertext_length(ptr, plainTextBytes.size.toULong())
        val cipherText = ByteArray(cipherTextLength.toInt())
        val mac = ByteArray(pk_mac_length(ptr).toInt())
        val ephemeral = ByteArray(pk_key_length().toInt())

        withRandom(pk_encrypt_random_length(ptr)) { random ->
            checkResult {
                pk_encrypt(ptr, plainTextBytes, cipherText, mac, ephemeral, random)
            }
        }
        return OlmPkMessage(
            cipherText.decodeToString(),
            mac.decodeToString(),
            ephemeral.decodeToString()
        )
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::pk_encryption_last_error)

}