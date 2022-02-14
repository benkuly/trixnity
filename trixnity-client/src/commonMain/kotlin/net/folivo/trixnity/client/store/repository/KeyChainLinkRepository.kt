package net.folivo.trixnity.client.store.repository

import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

interface KeyChainLinkRepository {
    suspend fun save(keyChainLink: KeyChainLink)
    suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink>
    suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key)
    suspend fun deleteAll()
}
