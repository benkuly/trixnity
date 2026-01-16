package de.connect2x.trixnity.client.store

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key

data class KeyChainLink(
    val signingUserId: UserId,
    val signingKey: Key.Ed25519Key,
    val signedUserId: UserId,
    val signedKey: Key.Ed25519Key
)