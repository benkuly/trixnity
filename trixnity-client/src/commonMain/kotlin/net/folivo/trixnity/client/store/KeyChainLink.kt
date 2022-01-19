package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

data class KeyChainLink(
    val signingUserId: UserId,
    val signingKey: Key.Ed25519Key,
    val signedUserId: UserId,
    val signedKey: Key.Ed25519Key
)