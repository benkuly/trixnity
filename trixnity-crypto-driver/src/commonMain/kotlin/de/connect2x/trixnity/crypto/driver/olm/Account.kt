package de.connect2x.trixnity.crypto.driver.olm

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Ed25519PublicKey
import de.connect2x.trixnity.crypto.driver.keys.Ed25519Signature
import de.connect2x.trixnity.crypto.driver.keys.PickleKey

interface Account : AutoCloseable {

    val ed25519Key: Ed25519PublicKey
    val curve25519Key: Curve25519PublicKey

    val maxNumberOfOneTimeKeys: Int
    val storedOneTimeKeyCount: Int

    val oneTimeKeys: Map<String, Curve25519PublicKey>
    val fallbackKey: Pair<String, Curve25519PublicKey>?

    fun sign(message: String): Ed25519Signature

    fun createOutboundSession(
        identityKey: Curve25519PublicKey,
        oneTimeKey: Curve25519PublicKey,
    ): Session

    fun createInboundSession(
        preKeyMessage: Message.PreKey,
        theirIdentityKey: Curve25519PublicKey? = null,
    ): InboundSessionCreationResult

    fun generateOneTimeKeys(
        count: Int,
    ): OneTimeKeyGenerationResult

    fun generateFallbackKey(): Curve25519PublicKey?

    fun forgetFallbackKey()

    fun markKeysAsPublished()

    fun pickle(pickleKey: PickleKey? = null): String
    fun dehydrate(pickleKey: PickleKey): DehydratedDevice

    interface OneTimeKeyGenerationResult {
        val created: List<Curve25519PublicKey>
        val removed: List<Curve25519PublicKey>

        operator fun component1() = created
        operator fun component2() = removed
    }

    interface InboundSessionCreationResult {
        val plaintext: String
        val session: Session

        operator fun component1() = plaintext
        operator fun component2() = session
    }
}