package de.connect2x.trixnity.crypto.driver.libolm.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import de.connect2x.trixnity.crypto.driver.libolm.rethrow
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSession
import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessage
import de.connect2x.trixnity.libolm.OlmInboundGroupSession
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmInboundGroupSession(val inner: OlmInboundGroupSession) : InboundGroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val firstKnownIndex: Int
        get() = inner.firstKnownIndex.toInt()

    override fun decrypt(message: MegolmMessage): InboundGroupSession.DecryptedMessage = rethrow {
        require(message is LibOlmMegolmMessage)

        val result = inner.decrypt(message.inner)

        InboundGroupSession.DecryptedMessage(
            plaintext = result.message, messageIndex = result.index.toInt()
        )
    }

    override fun exportAt(index: Int): LibOlmExportedSessionKey = LibOlmExportedSessionKey(inner.export(index.toLong()))


    override fun exportAtFirstKnownIndex(): LibOlmExportedSessionKey = exportAt(firstKnownIndex)

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.free()

}

