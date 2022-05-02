package net.folivo.trixnity.olm

import net.folivo.trixnity.olm.OlmLibrary.clear_session
import net.folivo.trixnity.olm.OlmLibrary.create_inbound_session
import net.folivo.trixnity.olm.OlmLibrary.create_inbound_session_from
import net.folivo.trixnity.olm.OlmLibrary.create_outbound_session
import net.folivo.trixnity.olm.OlmLibrary.create_outbound_session_random_length
import net.folivo.trixnity.olm.OlmLibrary.decrypt_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibrary.encrypt_message_length
import net.folivo.trixnity.olm.OlmLibrary.encrypt_message_type
import net.folivo.trixnity.olm.OlmLibrary.encrypt_random_length
import net.folivo.trixnity.olm.OlmLibrary.matches_inbound_session
import net.folivo.trixnity.olm.OlmLibrary.matches_inbound_session_from
import net.folivo.trixnity.olm.OlmLibrary.pickle_session
import net.folivo.trixnity.olm.OlmLibrary.pickle_session_length
import net.folivo.trixnity.olm.OlmLibrary.session
import net.folivo.trixnity.olm.OlmLibrary.session_describe
import net.folivo.trixnity.olm.OlmLibrary.session_has_received_message
import net.folivo.trixnity.olm.OlmLibrary.session_id
import net.folivo.trixnity.olm.OlmLibrary.session_id_length
import net.folivo.trixnity.olm.OlmLibrary.session_last_error
import net.folivo.trixnity.olm.OlmLibrary.unpickle_session

actual class OlmSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmSessionPointer = session()

    actual companion object {
        actual suspend fun createOutbound(
            account: OlmAccount,
            theirIdentityKey: String,
            theirOneTimeKey: String
        ): OlmSession = OlmSession().apply {
            withRandom(create_outbound_session_random_length(ptr)) { random ->
                checkResult {
                    create_outbound_session(
                        ptr,
                        account.ptr,
                        theirIdentityKey.encodeToByteArray(),
                        theirOneTimeKey.encodeToByteArray(),
                        random
                    )
                }
            }
        }

        actual suspend fun createInbound(
            account: OlmAccount,
            oneTimeKeyMessage: String
        ): OlmSession = OlmSession().apply {
            checkResult {
                create_inbound_session(ptr, account.ptr, oneTimeKeyMessage.encodeToByteArray())
            }
        }

        actual suspend fun createInboundFrom(
            account: OlmAccount,
            identityKey: String,
            oneTimeKeyMessage: String
        ): OlmSession = OlmSession().apply {
            checkResult {
                create_inbound_session_from(
                    ptr,
                    account.ptr,
                    identityKey.encodeToByteArray(),
                    oneTimeKeyMessage.encodeToByteArray()
                )
            }
        }

        actual suspend fun unpickle(key: String, pickle: String): OlmSession = OlmSession().apply {
            checkResult {
                unpickle_session(ptr, key.encodeToByteArray(), pickle.encodeToByteArray())
            }
        }
    }

    actual val sessionId: String
        get() {
            val id = ByteArray(session_id_length(ptr).toInt())
            val size = checkResult { session_id(ptr, id) }
            return id.decodeToString(endIndex = size.toInt())
        }

    actual val hasReceivedMessage: Boolean get() = session_has_received_message(ptr) != 0
    actual val description: String
        get() {
            // 256 from here https://gitlab.matrix.org/matrix-org/olm/-/blob/030e506c00bc3cd59f5e31f9a14a52a63c7c033a/javascript/olm_post.js#L491
            val bufferSize = 256
            val desc = ByteArray(bufferSize)
            session_describe(ptr, desc)
            return desc.decodeToString()
        }

    actual override fun free() {
        clear_session(ptr)
        ptr.free()
    }

    actual fun pickle(key: String): String = pickle(
        ptr,
        key,
        ::pickle_session_length,
        ::pickle_session,
        ::session_last_error
    )

    actual fun matchesInboundSession(oneTimeKeyMessage: String): Boolean =
        checkResult { matches_inbound_session(ptr, oneTimeKeyMessage.encodeToByteArray()) } == 1uL

    actual fun matchesInboundSessionFrom(identityKey: String, oneTimeKeyMessage: String): Boolean =
        checkResult {
            matches_inbound_session_from(
                ptr,
                identityKey.encodeToByteArray(),
                oneTimeKeyMessage.encodeToByteArray()
            )
        } == 1uL


    actual fun encrypt(plainText: String): OlmMessage {
        val messageType = encrypt_message_type(ptr)
        val plainTextBytes = plainText.encodeToByteArray()
        val message = ByteArray(encrypt_message_length(ptr, plainTextBytes.size.toULong()).toInt())
        val size = checkResult {
            withRandom(encrypt_random_length(ptr)) { random ->
                OlmLibrary.encrypt(ptr, plainTextBytes, random, message)
            }
        }
        return OlmMessage(
            message.decodeToString(endIndex = size.toInt()),
            OlmMessage.OlmMessageType.of(messageType.toInt())
        )
    }

    actual fun decrypt(message: OlmMessage): String {
        val plainText = ByteArray(checkResult {
            decrypt_max_plaintext_length(ptr, message.type.value.toULong(), message.cipherText.encodeToByteArray())
        }.toInt())

        val size = checkResult {
            OlmLibrary.decrypt(ptr, message.type.value.toULong(), message.cipherText.encodeToByteArray(), plainText)
        }
        return plainText.decodeToString(endIndex = size.toInt())
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::session_last_error)
}