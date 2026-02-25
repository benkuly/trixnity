package de.connect2x.trixnity.libolm

import kotlin.js.toInt

actual class OlmOutboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmOutboundGroupSessionPointer =
        rethrow { OutboundGroupSession() }

    actual companion object {
        actual fun create(): OlmOutboundGroupSession {
            return OlmOutboundGroupSession()
                .apply { rethrow { ptr.create() } }
        }

        actual fun unpickle(key: String?, pickle: String): OlmOutboundGroupSession {
            return OlmOutboundGroupSession()
                .apply { rethrow { ptr.unpickle(key ?: "", pickle) } }
        }
    }

    actual val sessionId: String get() = rethrow { ptr.session_id() }
    actual val sessionKey: String get() = rethrow { ptr.session_key() }
    actual val messageIndex: Long get() = rethrow { ptr.message_index().toInt().toLong() }

    actual override fun free() = ptr.free()

    actual fun pickle(key: String?): String = rethrow { ptr.pickle(key ?: "") }

    actual fun encrypt(plainText: String): String = rethrow { ptr.encrypt(plainText) }

}