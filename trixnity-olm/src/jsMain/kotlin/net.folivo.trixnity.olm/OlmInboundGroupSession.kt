package net.folivo.trixnity.olm

actual class OlmInboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmInboundGroupSessionPointer =
        rethrow { js("new Olm.InboundGroupSession()") }.unsafeCast<OlmInboundGroupSessionPointer>()

    actual companion object {
        actual suspend fun create(sessionKey: String): OlmInboundGroupSession {
            initOlm()
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.create(sessionKey) } }
        }

        actual suspend fun import(sessionKey: String): OlmInboundGroupSession {
            initOlm()
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.import_session(sessionKey) } }
        }

        actual suspend fun unpickle(key: String, pickle: String): OlmInboundGroupSession {
            initOlm()
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.unpickle(key, pickle) } }
        }
    }

    actual val sessionId: String get() = rethrow { ptr.session_id() }
    actual val firstKnownIndex: Long get() = rethrow { ptr.first_known_index() }.toLong()

    actual override fun free() = ptr.free()

    actual fun export(messageIndex: Long): String = rethrow { ptr.export_session(messageIndex.toInt()) }

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun decrypt(encryptedText: String): OlmInboundGroupMessage {
        val message = rethrow { ptr.decrypt(encryptedText) }
        return OlmInboundGroupMessage(message.plaintext, message.message_index.toLong())
    }


}