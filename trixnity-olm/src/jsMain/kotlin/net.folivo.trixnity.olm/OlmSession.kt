package net.folivo.trixnity.olm

actual class OlmSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmSessionPointer = rethrow { js("new Olm.Session()") }.unsafeCast<OlmSessionPointer>()

    actual companion object {
        actual suspend fun createOutbound(
            account: OlmAccount,
            theirIdentityKey: String,
            theirOneTimeKey: String
        ): OlmSession {
            initOlm()
            return OlmSession().apply {
                rethrow { ptr.create_outbound(account.ptr, theirIdentityKey, theirOneTimeKey) }
            }
        }

        actual suspend fun createInbound(
            account: OlmAccount,
            oneTimeKeyMessage: String
        ): OlmSession {
            initOlm()
            return OlmSession().apply {
                rethrow { ptr.create_inbound(account.ptr, oneTimeKeyMessage) }
            }
        }

        actual suspend fun createInboundFrom(
            account: OlmAccount,
            identityKey: String,
            oneTimeKeyMessage: String
        ): OlmSession {
            initOlm()
            return OlmSession().apply {
                rethrow { ptr.create_inbound_from(account.ptr, identityKey, oneTimeKeyMessage) }
            }
        }

        actual suspend fun unpickle(key: String, pickle: String): OlmSession {
            initOlm()
            return OlmSession().apply {
                rethrow { ptr.unpickle(key, pickle) }
            }
        }
    }

    actual val sessionId: String get() = rethrow { ptr.session_id() }
    actual val hasReceivedMessage: Boolean get() = rethrow { ptr.has_received_message() }
    actual val description: String get() = rethrow { ptr.describe() }

    actual override fun free() = ptr.free()

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun matchesInboundSession(oneTimeKeyMessage: String): Boolean =
        rethrow { ptr.matches_inbound(oneTimeKeyMessage) }

    actual fun matchesInboundSessionFrom(identityKey: String, oneTimeKeyMessage: String): Boolean =
        rethrow { ptr.matches_inbound_from(identityKey, oneTimeKeyMessage) }

    actual fun encrypt(plainText: String): OlmMessage {
        val message = rethrow { ptr.encrypt(plainText) }
        return OlmMessage(message.body, OlmMessage.OlmMessageType.of(message.type.toInt()))
    }

    actual fun decrypt(message: OlmMessage): String = rethrow {
        ptr.decrypt(message.type.value, message.cipherText)
    }
}


