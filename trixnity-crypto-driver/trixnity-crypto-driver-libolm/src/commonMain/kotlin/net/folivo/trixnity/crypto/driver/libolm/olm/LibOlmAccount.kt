package net.folivo.trixnity.crypto.driver.libolm.olm

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmEd25519PublicKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmEd25519Signature
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmPickleKey
import net.folivo.trixnity.crypto.driver.libolm.rethrow
import net.folivo.trixnity.crypto.driver.olm.Account
import net.folivo.trixnity.crypto.driver.olm.Message
import net.folivo.trixnity.crypto.driver.olm.Session
import net.folivo.trixnity.libolm.OlmAccount
import net.folivo.trixnity.libolm.OlmMessage
import net.folivo.trixnity.libolm.OlmSession
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmAccount(
    private val inner: OlmAccount,
) : Account {
    override val ed25519Key: LibOlmEd25519PublicKey
        get() = LibOlmEd25519PublicKey(inner.identityKeys.ed25519)

    override val curve25519Key: LibOlmCurve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.identityKeys.curve25519)

    override val maxNumberOfOneTimeKeys: Int
        get() = inner.maxNumberOfOneTimeKeys.toInt()

    override val storedOneTimeKeyCount: Int
        get() = inner.oneTimeKeys.curve25519.size

    override val oneTimeKeys: Map<String, LibOlmCurve25519PublicKey>
        get() = inner.oneTimeKeys.curve25519.mapValues {
            LibOlmCurve25519PublicKey(
                it.value
            )
        }

    override val fallbackKey: Pair<String, LibOlmCurve25519PublicKey>?
        get() = with(inner.unpublishedFallbackKey.curve25519) {
            when (size) {
                0 -> null
                1 -> entries.first().let {
                    it.key to LibOlmCurve25519PublicKey(
                        it.value
                    )
                }

                else -> error("internal error: too many fallback keys: $this")
            }
        }

    override fun sign(message: String): LibOlmEd25519Signature = LibOlmEd25519Signature(inner.sign(message))

    override fun createOutboundSession(
        identityKey: Curve25519PublicKey,
        oneTimeKey: Curve25519PublicKey,
    ): Session {
        require(identityKey is LibOlmCurve25519PublicKey)
        require(oneTimeKey is LibOlmCurve25519PublicKey)

        val session = OlmSession.createOutbound(
            inner,
            identityKey.inner,
            oneTimeKey.inner,
        )

        return LibOlmSession(session)
    }

    override fun createInboundSession(
        preKeyMessage: Message.PreKey,
        theirIdentityKey: Curve25519PublicKey?,
    ): InboundSessionCreationResult = rethrow {
        require(preKeyMessage is LibOlmPreKeyMessage)
        require(theirIdentityKey == null || theirIdentityKey is LibOlmCurve25519PublicKey)

        val session = when (theirIdentityKey) {
            null -> OlmSession.createInbound(
                inner, preKeyMessage.inner
            )

            else -> OlmSession.createInboundFrom(
                inner,
                theirIdentityKey.inner,
                preKeyMessage.inner,
            )
        }

        inner.removeOneTimeKeys(session)

        val plaintext = session.decrypt(
            OlmMessage(preKeyMessage.inner, OlmMessage.OlmMessageType.INITIAL_PRE_KEY)
        )

        InboundSessionCreationResult(
            plaintext = plaintext, session = LibOlmSession(session)
        )
    }

    override fun generateOneTimeKeys(count: Int): OneTimeKeyGenerationResult {
        val previous = oneTimeKeys
        inner.generateOneTimeKeys(count.toLong())
        val new = oneTimeKeys

        val created = new.entries - previous.entries
        val removed = previous.entries - new.entries

        return OneTimeKeyGenerationResult(
            created = created.map { it.value },
            removed = removed.map { it.value },
        )
    }

    override fun generateFallbackKey(): LibOlmCurve25519PublicKey? {
        val previous = fallbackKey
        inner.generateFallbackKey()

        return previous?.second
    }

    override fun forgetFallbackKey() {
        inner.forgetOldFallbackKey()
    }

    override fun markKeysAsPublished() = inner.markKeysAsPublished()

    override fun pickle(pickleKey: PickleKey?): String {
        require(pickleKey == null || pickleKey is LibOlmPickleKey)

        return inner.pickle(pickleKey?.inner)
    }

    override fun close() = inner.free()


    data class InboundSessionCreationResult(
        override val plaintext: String,
        override val session: LibOlmSession,
    ) : Account.InboundSessionCreationResult

    data class OneTimeKeyGenerationResult(
        override val created: List<LibOlmCurve25519PublicKey>,
        override val removed: List<LibOlmCurve25519PublicKey>,
    ) : Account.OneTimeKeyGenerationResult
}