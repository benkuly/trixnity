package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_sas
import net.folivo.trixnity.olm.OlmLibrary.create_sas
import net.folivo.trixnity.olm.OlmLibrary.create_sas_random_length
import net.folivo.trixnity.olm.OlmLibrary.sas
import net.folivo.trixnity.olm.OlmLibrary.sas_calculate_mac
import net.folivo.trixnity.olm.OlmLibrary.sas_calculate_mac_fixed_base64
import net.folivo.trixnity.olm.OlmLibrary.sas_generate_bytes
import net.folivo.trixnity.olm.OlmLibrary.sas_get_pubkey
import net.folivo.trixnity.olm.OlmLibrary.sas_last_error
import net.folivo.trixnity.olm.OlmLibrary.sas_mac_length
import net.folivo.trixnity.olm.OlmLibrary.sas_pubkey_length
import net.folivo.trixnity.olm.OlmLibrary.sas_set_their_key

actual class OlmSAS private constructor() : WantsToBeFree {
    internal actual val ptr: OlmSASPointer = sas()

    actual companion object {
        actual fun create(): OlmSAS = OlmSAS().apply {
            try {
                val result = withRandom(create_sas_random_length(ptr)) { random ->
                    create_sas(ptr, random)
                }
                checkError(ptr, result, ::sas_last_error)
            } catch (e: Exception) {
                free()
                throw e
            }
        }
    }

    actual val publicKey: String
        get() {
            val pubkey = ByteArray(sas_pubkey_length(ptr).toInt())
            checkResult { sas_get_pubkey(ptr, pubkey) }
            return pubkey.decodeToString()
        }

    actual override fun free() {
        clear_sas(ptr)
        ptr.free()
    }

    actual fun setTheirPublicKey(theirPublicKey: String) {
        checkResult { sas_set_their_key(ptr, theirPublicKey.encodeToByteArray()) }
    }

    actual fun generateShortCode(info: String, numberOfBytes: Int): ByteArray {
        val shortCode = ByteArray(numberOfBytes)
        checkResult { sas_generate_bytes(ptr, info.encodeToByteArray(), shortCode) }
        return shortCode
    }

    actual fun calculateMac(input: String, info: String): String {
        val mac = ByteArray(sas_mac_length(ptr).toInt())
        checkResult { sas_calculate_mac(ptr, input.encodeToByteArray(), info.encodeToByteArray(), mac) }
        return mac.decodeToString()
    }

    actual fun calculateMacFixedBase64(input: String, info: String): String {
        val mac = ByteArray(sas_mac_length(ptr).toInt())
        checkResult { sas_calculate_mac_fixed_base64(ptr, input.encodeToByteArray(), info.encodeToByteArray(), mac) }
        return mac.decodeToString()
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::sas_last_error)
}