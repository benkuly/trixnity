package net.folivo.trixnity.crypto.driver.libvodozemac.megolm

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.rethrow
import net.folivo.trixnity.crypto.driver.megolm.ExportedSessionKey
import net.folivo.trixnity.crypto.driver.megolm.InboundGroupSession
import net.folivo.trixnity.crypto.driver.megolm.MegolmMessage
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.megolm.InboundGroupSession as Inner

@JvmInline
value class LibVodozemacInboundGroupSession(val inner: Inner) : InboundGroupSession {
    override val sessionId: String
        get() = inner.sessionId

    override val firstKnownIndex: Int
        get() = inner.firstKnownIndex

    override fun exportAt(index: Int): LibVodozemacExportedSessionKey?
        = inner.exportAt(index)?.let(::LibVodozemacExportedSessionKey)

    override fun exportAtFirstKnownIndex(): ExportedSessionKey
        = inner.exportAtFirstKnownIndex().let(::LibVodozemacExportedSessionKey)

    override fun decrypt(message: MegolmMessage): InboundGroupSession.DecryptedMessage = rethrow {
        require(message is LibVodozemacMegolmMessage)

        val result = inner.decrypt(message.inner)

        InboundGroupSession.DecryptedMessage(
            plaintext = result.plaintext.value,
            messageIndex = result.messageIndex,
        )
    }

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close()
        = inner.close()
}