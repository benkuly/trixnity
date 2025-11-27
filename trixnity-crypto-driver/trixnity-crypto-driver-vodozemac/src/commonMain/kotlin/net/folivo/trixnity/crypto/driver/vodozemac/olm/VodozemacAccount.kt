package net.folivo.trixnity.crypto.driver.vodozemac.olm

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacEd25519PublicKey
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacEd25519Signature
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacPickleKey
import net.folivo.trixnity.crypto.driver.vodozemac.rethrow
import net.folivo.trixnity.crypto.driver.olm.Account
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.Session
import net.folivo.trixnity.vodozemac.olm.Account as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacAccount(val inner: Inner) : Account {

    override val ed25519Key: VodozemacEd25519PublicKey
        get() = VodozemacEd25519PublicKey(inner.ed25519Key)

    override val curve25519Key: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.curve25519Key)

    override val maxNumberOfOneTimeKeys: Int
        get() = inner.maxNumberOfOneTimeKeys

    override val storedOneTimeKeyCount: Int
        get() = inner.storedOneTimeKeyCount

    override val oneTimeKeys: Map<String, VodozemacCurve25519PublicKey>
        get() = inner.oneTimeKeys.mapValues { VodozemacCurve25519PublicKey(it.value) }

    override val fallbackKey: Pair<String, VodozemacCurve25519PublicKey>?
        get() = inner.fallbackKey?.let { it.first to VodozemacCurve25519PublicKey(it.second) }

    override fun sign(message: String): VodozemacEd25519Signature = VodozemacEd25519Signature(inner.sign(message))

    override fun createOutboundSession(
        identityKey: Curve25519PublicKey,
        oneTimeKey: Curve25519PublicKey,
    ): Session {
        require(identityKey is VodozemacCurve25519PublicKey)
        require(oneTimeKey is VodozemacCurve25519PublicKey)

        val session = inner.createOutboundSession(
            identityKey.inner,
            oneTimeKey.inner,
        )

        return VodozemacSession(session)
    }

    override fun createInboundSession(
        preKeyMessage: Message.PreKey,
        theirIdentityKey: Curve25519PublicKey?,
    ): InboundSessionCreationResult = rethrow {
        require(preKeyMessage is VodozemacPreKeyMessage)
        require(theirIdentityKey == null || theirIdentityKey is VodozemacCurve25519PublicKey)

        val (plaintext, session) = inner.createInboundSession(
            preKeyMessage.inner,
            theirIdentityKey?.inner ?: preKeyMessage.inner.sessionKeys.identityKey,
        )

        InboundSessionCreationResult(
            plaintext = plaintext,
            session = VodozemacSession(session)
        )
    }

    override fun generateOneTimeKeys(count: Int): OneTimeKeyGenerationResult {
        val (created, removed) = inner.generateOneTimeKeys(count)

        return OneTimeKeyGenerationResult(
            created = created.map(::VodozemacCurve25519PublicKey),
            removed = removed.map(::VodozemacCurve25519PublicKey),
        )
    }

    override fun generateFallbackKey(): VodozemacCurve25519PublicKey? =
        inner.generateFallbackKey()?.let(::VodozemacCurve25519PublicKey)

    override fun forgetFallbackKey() {
        inner.forgetFallbackKey()
    }

    override fun markKeysAsPublished() = inner.markKeysAsPublished()

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is VodozemacPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.close()

    data class InboundSessionCreationResult(
        override val plaintext: String,
        override val session: VodozemacSession,
    ) : Account.InboundSessionCreationResult

    data class OneTimeKeyGenerationResult(
        override val created: List<VodozemacCurve25519PublicKey>,
        override val removed: List<VodozemacCurve25519PublicKey>,
    ) : Account.OneTimeKeyGenerationResult
}