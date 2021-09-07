package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.crypto.SignedSerializer

@Serializable(with = SignedSerializer::class)
open class Signed<T, U>(
    val signed: T,
    val signatures: Signatures<U>
)