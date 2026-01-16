package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key

interface KeyChainLinkRepository {
    suspend fun save(keyChainLink: KeyChainLink)
    suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink>
    suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key)
    suspend fun deleteAll()
}
