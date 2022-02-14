package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

@Serializable
data class KeyChainLink(
    val signingUserId: UserId,
    val signingKey: Key.Ed25519Key,
    val signedUserId: UserId,
    val signedKey: Key.Ed25519Key
)