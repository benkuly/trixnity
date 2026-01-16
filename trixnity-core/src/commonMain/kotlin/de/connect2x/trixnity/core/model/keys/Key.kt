package de.connect2x.trixnity.core.model.keys

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm.Unknown
import de.connect2x.trixnity.core.model.keys.KeyValue.*


sealed interface Key {
    val algorithm: KeyAlgorithm
    val id: String?
    val value: KeyValue

    val fullId: String
        get() = if (id.isNullOrEmpty()) algorithm.name else "${algorithm.name}:$id"

    data class Ed25519Key(
        override val id: String?,
        override val value: Ed25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String) : this(id, Ed25519KeyValue(value))

        override val algorithm: KeyAlgorithm.Ed25519 = KeyAlgorithm.Ed25519
    }

    data class Curve25519Key(
        override val id: String?,
        override val value: Curve25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String) : this(id, Curve25519KeyValue(value))

        override val algorithm: KeyAlgorithm.Curve25519 = KeyAlgorithm.Curve25519
    }

    data class SignedCurve25519Key(
        override val id: String?,
        override val value: SignedCurve25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String, fallback: Boolean? = null, signatures: Signatures<UserId>)
                : this(id, SignedCurve25519KeyValue(value, fallback, signatures))

        override val algorithm: KeyAlgorithm.SignedCurve25519 = KeyAlgorithm.SignedCurve25519
    }

    data class UnknownKey(
        override val id: String?,
        override val value: UnknownKeyValue,
        override val algorithm: Unknown,
    ) : Key

    companion object
}