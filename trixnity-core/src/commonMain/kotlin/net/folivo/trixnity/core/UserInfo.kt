package net.folivo.trixnity.core

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

data class UserInfo(
    val userId: UserId,
    val deviceId: String,
    val signingPublicKey: Key.Ed25519Key,
    val identityPublicKey: Key.Curve25519Key,
)