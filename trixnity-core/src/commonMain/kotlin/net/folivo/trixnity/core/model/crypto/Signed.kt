package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.SignedSerializer

@Serializable(with = SignedSerializer::class)
open class Signed<T, U>(
    val signed: T,
    val signatures: Signatures<U>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as Signed<*, *>

        if (signed != other.signed) return false
        if (signatures != other.signatures) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signed?.hashCode() ?: 0
        result = 31 * result + signatures.hashCode()
        return result
    }

    override fun toString(): String {
        return "Signed(signed=$signed, signatures=$signatures)"
    }
}