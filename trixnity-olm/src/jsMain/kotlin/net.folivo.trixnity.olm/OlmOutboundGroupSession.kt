package net.folivo.trixnity.olm

import rethrow


actual class OlmOutboundGroupSession private constructor() : WantsToBeFree {
    internal actual val ptr: OlmOutboundGroupSessionPointer =
        rethrow { js("new Olm.OutboundGroupSession()") }.unsafeCast<OlmOutboundGroupSessionPointer>()

    actual companion object {
        actual fun create(): OlmOutboundGroupSession =
            OlmOutboundGroupSession()
                .apply { rethrow { ptr.create() } }

        actual fun unpickle(key: String, pickle: String): OlmOutboundGroupSession =
            OlmOutboundGroupSession()
                .apply { rethrow { ptr.unpickle(key, pickle) } }
    }

    actual val sessionId: String get() = rethrow { ptr.session_id() }
    actual val sessionKey: String get() = rethrow { ptr.session_key() }
    actual val messageIndex: Long get() = rethrow { ptr.message_index().toLong() }

    actual override fun free() = ptr.free()

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun encrypt(plainText: String): String = rethrow { ptr.encrypt(plainText) }

}