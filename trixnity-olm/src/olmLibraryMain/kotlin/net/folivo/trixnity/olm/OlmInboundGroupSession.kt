package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.export_inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.export_inbound_group_session_length
import net.folivo.trixnity.olm.OlmLibrary.group_decrypt
import net.folivo.trixnity.olm.OlmLibrary.group_decrypt_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibrary.import_inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.inbound_group_session_first_known_index
import net.folivo.trixnity.olm.OlmLibrary.inbound_group_session_id
import net.folivo.trixnity.olm.OlmLibrary.inbound_group_session_id_length
import net.folivo.trixnity.olm.OlmLibrary.inbound_group_session_last_error
import net.folivo.trixnity.olm.OlmLibrary.init_inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.pickle_inbound_group_session
import net.folivo.trixnity.olm.OlmLibrary.pickle_inbound_group_session_length
import net.folivo.trixnity.olm.OlmLibrary.unpickle_inbound_group_session

actual class OlmInboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmInboundGroupSessionPointer = inbound_group_session()

    actual companion object {
        actual suspend fun create(sessionKey: String): OlmInboundGroupSession =
            OlmInboundGroupSession().apply {
                try {
                    val result = init_inbound_group_session(ptr, sessionKey.encodeToByteArray())
                    checkError(ptr, result, ::inbound_group_session_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }

        actual suspend fun import(sessionKey: String): OlmInboundGroupSession =
            OlmInboundGroupSession().apply {
                try {
                    val result = import_inbound_group_session(ptr, sessionKey.encodeToByteArray())
                    checkError(ptr, result, ::inbound_group_session_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }

        actual suspend fun unpickle(key: String, pickle: String): OlmInboundGroupSession =
            OlmInboundGroupSession().apply {
                try {
                    val result =
                        unpickle_inbound_group_session(ptr, key.encodeToByteArray(), pickle.encodeToByteArray())
                    checkError(ptr, result, ::inbound_group_session_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }
    }

    actual val sessionId: String
        get() {
            val sessionId = ByteArray(inbound_group_session_id_length(ptr).toInt())
            val size = checkResult { inbound_group_session_id(ptr, sessionId) }
            return sessionId.decodeToString(endIndex = size.toInt())
        }

    actual val firstKnownIndex: Long get() = inbound_group_session_first_known_index(ptr).toLong()

    actual override fun free() {
        clear_inbound_group_session(ptr)
        ptr.free()
    }

    actual fun export(messageIndex: Long): String {
        val export = ByteArray(export_inbound_group_session_length(ptr).toInt())
        val size = checkResult { export_inbound_group_session(ptr, export, messageIndex.toUInt()) }
        return export.decodeToString(endIndex = size.toInt())
    }

    actual fun pickle(key: String): String = pickle(
        ptr,
        key,
        ::pickle_inbound_group_session_length,
        ::pickle_inbound_group_session,
        ::inbound_group_session_last_error
    )

    actual fun decrypt(encryptedText: String): OlmInboundGroupMessage {
        val maxPlainTextLength =
            checkResult { group_decrypt_max_plaintext_length(ptr, encryptedText.encodeToByteArray()) }
        val plainText = ByteArray(maxPlainTextLength.toInt())
        val messageIndex = MutableWrapper(0u)
        val size = checkResult { group_decrypt(ptr, encryptedText.encodeToByteArray(), plainText, messageIndex) }

        return OlmInboundGroupMessage(
            plainText.decodeToString(endIndex = size.toInt()),
            messageIndex.value.toLong()
        )
    }

    private fun checkResult(block: () -> ULong): ULong =
        checkError(ptr, block(), ::inbound_group_session_last_error)
}