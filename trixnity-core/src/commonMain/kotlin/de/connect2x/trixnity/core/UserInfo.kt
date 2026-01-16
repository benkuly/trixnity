package de.connect2x.trixnity.core

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key

data class UserInfo(
    val userId: UserId,
    val deviceId: String,
    val signingPublicKey: Key.Ed25519Key,
    val identityPublicKey: Key.Curve25519Key,
)