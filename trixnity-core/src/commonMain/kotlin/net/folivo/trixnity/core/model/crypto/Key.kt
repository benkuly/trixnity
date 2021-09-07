package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm.Unknown
import net.folivo.trixnity.core.serialization.crypto.Curve25519KeySerializer
import net.folivo.trixnity.core.serialization.crypto.Ed25519KeySerializer

sealed interface Key {
    val algorithm: KeyAlgorithm

    @Serializable(with = Ed25519KeySerializer::class)
    data class Ed25519Key(
        val keyId: String? = null,
        val value: String,
        override val algorithm: KeyAlgorithm.Ed25519 = KeyAlgorithm.Ed25519
    ) : Key

    @Serializable(with = Curve25519KeySerializer::class)
    data class Curve25519Key(
        val keyId: String? = null,
        val value: String,
        override val algorithm: KeyAlgorithm.Curve25519 = KeyAlgorithm.Curve25519
    ) : Key

    class SignedCurve25519Key(
        keyId: String? = null,
        value: String,
        signatures: Signatures<UserId>,
        override val algorithm: KeyAlgorithm.SignedCurve25519 = KeyAlgorithm.SignedCurve25519
    ) : Key, Signed<Curve25519Key, UserId>(Curve25519Key(keyId, value), signatures)

    data class UnknownKey(
        val keyId: String? = null,
        val value: JsonElement,
        override val algorithm: Unknown
    ) : Key
}