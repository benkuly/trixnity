package net.folivo.trixnity.libolm

import net.folivo.trixnity.libolm.OlmLibrary.clear_utility
import net.folivo.trixnity.libolm.OlmLibrary.sha256
import net.folivo.trixnity.libolm.OlmLibrary.sha256_length
import net.folivo.trixnity.libolm.OlmLibrary.utility
import net.folivo.trixnity.libolm.OlmLibrary.utility_last_error

actual class OlmUtility private constructor() : WantsToBeFree {
    internal actual val ptr: OlmUtilityPointer = utility()

    actual companion object {
        actual fun create(): OlmUtility = OlmUtility()
    }

    actual override fun free() {
        clear_utility(ptr)
        ptr.free()
    }

    actual fun sha256(input: ByteArray): String {
        val output = ByteArray(sha256_length(ptr).toInt())
        val size = checkResult { sha256(ptr, input, output) }
        return output.decodeToString(endIndex = size.toInt())
    }

    actual fun sha256(input: String): String {
        val output = ByteArray(sha256_length(ptr).toInt())
        val size = checkResult { sha256(ptr, input.encodeToByteArray(), output) }
        return output.decodeToString(endIndex = size.toInt())
    }

    actual fun verifyEd25519(key: String, message: String, signature: String) {
        checkResult {
            OlmLibrary.ed25519_verify(
                ptr,
                key.encodeToByteArray(),
                message.encodeToByteArray(),
                signature.encodeToByteArray()
            )
        }
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::utility_last_error)
}