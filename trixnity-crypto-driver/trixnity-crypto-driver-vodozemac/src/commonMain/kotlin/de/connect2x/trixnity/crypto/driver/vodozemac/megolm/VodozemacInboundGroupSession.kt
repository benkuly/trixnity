package de.connect2x.trixnity.crypto.driver.vodozemac.megolm

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import de.connect2x.trixnity.crypto.driver.vodozemac.rethrow
import de.connect2x.trixnity.crypto.driver.megolm.ExportedSessionKey
import de.connect2x.trixnity.crypto.driver.megolm.InboundGroupSession
import de.connect2x.trixnity.crypto.driver.megolm.MegolmMessage
import kotlin.jvm.JvmInline
import de.connect2x.trixnity.vodozemac.megolm.InboundGroupSession as Inner

@JvmInline
value class VodozemacInboundGroupSession(val inner: Inner) : InboundGroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val firstKnownIndex: Int
        get() = inner.firstKnownIndex

    override fun exportAt(index: Int): VodozemacExportedSessionKey? =
        inner.exportAt(index)?.let(::VodozemacExportedSessionKey)

    override fun exportAtFirstKnownIndex(): ExportedSessionKey =
        inner.exportAtFirstKnownIndex().let(::VodozemacExportedSessionKey)

    override fun decrypt(message: MegolmMessage): InboundGroupSession.DecryptedMessage = rethrow {
        require(message is VodozemacMegolmMessage)

        val result = inner.decrypt(message.inner)

        InboundGroupSession.DecryptedMessage(
            plaintext = result.plaintext.value,
            messageIndex = result.messageIndex,
        )
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.close()
}