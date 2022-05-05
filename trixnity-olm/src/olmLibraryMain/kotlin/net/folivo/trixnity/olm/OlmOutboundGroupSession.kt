package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_outbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.group_encrypt
import net.folivo.trixnity.olm.OlmLibrary.group_encrypt_message_length
import net.folivo.trixnity.olm.OlmLibrary.init_outbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.init_outbound_group_session_random_length
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_id
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_id_length
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_key
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_key_length
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_last_error
import net.folivo.trixnity.olm.OlmLibrary.outbound_group_session_message_index
import net.folivo.trixnity.olm.OlmLibrary.pickle_outbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.pickle_outbound_group_session_length
import net.folivo.trixnity.olm.OlmLibrary.unpickle_outbound_group_session

actual class OlmOutboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmOutboundGroupSessionPointer = outbound_group_session()

    actual companion object {
        actual suspend fun create(): OlmOutboundGroupSession =
            OlmOutboundGroupSession().apply {
                try {
                    val result = withRandom(init_outbound_group_session_random_length(ptr)) { random ->
                        init_outbound_group_session(ptr, random)
                    }
                    checkError(ptr, result, ::outbound_group_session_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }

        actual suspend fun unpickle(key: String, pickle: String): OlmOutboundGroupSession =
            OlmOutboundGroupSession().apply {
                try {
                    val result =
                        unpickle_outbound_group_session(ptr, key.encodeToByteArray(), pickle.encodeToByteArray())
                    checkError(ptr, result, ::outbound_group_session_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }
    }

    actual val sessionId: String
        get() {
            val sessionId = ByteArray(outbound_group_session_id_length(ptr).toInt())
            val size = checkResult { outbound_group_session_id(ptr, sessionId) }
            return sessionId.decodeToString(endIndex = size.toInt())
        }

    actual val sessionKey: String
        get() {
            val sessionKey = ByteArray(outbound_group_session_key_length(ptr).toInt())
            val size = checkResult { outbound_group_session_key(ptr, sessionKey) }
            return sessionKey.decodeToString(endIndex = size.toInt())
        }

    actual val messageIndex: Long
        get() = outbound_group_session_message_index(ptr).toLong()

    actual override fun free() {
        clear_outbound_group_session(ptr)
        ptr.free()
    }

    actual fun pickle(key: String): String =
        pickle(
            ptr,
            key,
            ::pickle_outbound_group_session_length,
            ::pickle_outbound_group_session,
            ::outbound_group_session_last_error
        )

    actual fun encrypt(plainText: String): String {
        val plainTextBytes = plainText.encodeToByteArray()
        val encryptedText = ByteArray(group_encrypt_message_length(ptr, plainTextBytes.size.toULong()).toInt())
        val size = checkResult { group_encrypt(ptr, plainTextBytes, encryptedText) }
        return encryptedText.decodeToString(endIndex = size.toInt())
    }

    private fun checkResult(block: () -> ULong): ULong =
        checkError(ptr, block(), ::outbound_group_session_last_error)
}