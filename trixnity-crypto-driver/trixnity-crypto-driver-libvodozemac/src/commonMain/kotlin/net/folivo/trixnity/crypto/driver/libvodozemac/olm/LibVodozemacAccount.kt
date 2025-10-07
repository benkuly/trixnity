package net.folivo.trixnity.crypto.driver.libvodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacEd25519PublicKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacEd25519Signature
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacPickleKey
import net.folivo.trixnity.crypto.driver.libvodozemac.rethrow
import net.folivo.trixnity.crypto.driver.olm.Account
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.Session
import net.folivo.trixnity.vodozemac.olm.Account as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacAccount(val inner: Inner) : Account {

    override val ed25519Key: LibVodozemacEd25519PublicKey
        get() = LibVodozemacEd25519PublicKey(inner.ed25519Key)

    override val curve25519Key: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.curve25519Key)

    override val maxNumberOfOneTimeKeys: Int
        get() = inner.maxNumberOfOneTimeKeys

    override val storedOneTimeKeyCount: Int
        get() = inner.storedOneTimeKeyCount

    override val oneTimeKeys: Map<String, LibVodozemacCurve25519PublicKey>
        get() = inner.oneTimeKeys.mapValues { LibVodozemacCurve25519PublicKey(it.value) }

    override val fallbackKey: Pair<String, LibVodozemacCurve25519PublicKey>?
        get() = inner.fallbackKey?.let { it.first to LibVodozemacCurve25519PublicKey(it.second) }

    override fun sign(message: String): LibVodozemacEd25519Signature
        = LibVodozemacEd25519Signature(inner.sign(message))

    override fun createOutboundSession(
        identityKey: Curve25519PublicKey,
        oneTimeKey: Curve25519PublicKey,
    ): Session {
        require(identityKey is LibVodozemacCurve25519PublicKey)
        require(oneTimeKey is LibVodozemacCurve25519PublicKey)

        val session = inner.createOutboundSession(
            identityKey.inner,
            oneTimeKey.inner,
        )

        return LibVodozemacSession(session)
    }

    override fun createInboundSession(
        preKeyMessage: Message.PreKey,
        theirIdentityKey: Curve25519PublicKey?,
    ): InboundSessionCreationResult = rethrow {
        require(preKeyMessage is LibVodozemacPreKeyMessage)
        require(theirIdentityKey == null || theirIdentityKey is LibVodozemacCurve25519PublicKey)

        val (plaintext, session) = inner.createInboundSession(
            preKeyMessage.inner,
            theirIdentityKey?.inner ?: preKeyMessage.inner.sessionKeys.identityKey,
        )

        InboundSessionCreationResult(
            plaintext = plaintext,
            session = LibVodozemacSession(session)
        )
    }

    override fun generateOneTimeKeys(count: Int): OneTimeKeyGenerationResult {
        val (created, removed) = inner.generateOneTimeKeys(count)

        return OneTimeKeyGenerationResult(
            created = created.map(::LibVodozemacCurve25519PublicKey),
            removed = removed.map(::LibVodozemacCurve25519PublicKey),
        )
    }

    override fun generateFallbackKey(): LibVodozemacCurve25519PublicKey?
        = inner.generateFallbackKey()?.let(::LibVodozemacCurve25519PublicKey)

    override fun forgetFallbackKey() {
        inner.forgetFallbackKey()
    }

    override fun markKeysAsPublished()
        = inner.markKeysAsPublished()

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibVodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close()
        = inner.close()

    data class InboundSessionCreationResult(
        override val plaintext: String,
        override val session: LibVodozemacSession,
    ) : Account.InboundSessionCreationResult

    data class OneTimeKeyGenerationResult(
        override val created: List<LibVodozemacCurve25519PublicKey>,
        override val removed: List<LibVodozemacCurve25519PublicKey>,
    ) : Account.OneTimeKeyGenerationResult
}