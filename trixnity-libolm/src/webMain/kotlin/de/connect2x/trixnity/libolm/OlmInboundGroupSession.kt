package de.connect2x.trixnity.libolm

import kotlin.js.toDouble
import kotlin.js.toJsNumber

actual class OlmInboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmInboundGroupSessionPointer =
        rethrow { InboundGroupSession() }

    actual companion object {
        actual fun create(sessionKey: String): OlmInboundGroupSession {
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.create(sessionKey) } }
        }

        actual fun import(sessionKey: String): OlmInboundGroupSession {
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.import_session(sessionKey) } }
        }

        actual fun unpickle(key: String?, pickle: String): OlmInboundGroupSession {
            return OlmInboundGroupSession()
                .apply { rethrow { ptr.unpickle(key ?: "", pickle) } }
        }
    }

    actual val sessionId: String get() = rethrow { ptr.session_id() }
    actual val firstKnownIndex: Long get() = rethrow { ptr.first_known_index() }.toDouble().toLong()

    actual override fun free() = ptr.free()

    actual fun export(messageIndex: Long): String = rethrow { ptr.export_session(messageIndex.toInt().toJsNumber()) }

    actual fun pickle(key: String?): String = rethrow { ptr.pickle(key ?: "") }

    actual fun decrypt(encryptedText: String): OlmInboundGroupMessage {
        val message = rethrow { ptr.decrypt(encryptedText) }
        return OlmInboundGroupMessage(message.plaintext, message.message_index.toDouble().toLong())
    }


}