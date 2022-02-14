package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import kotlin.coroutines.CoroutineContext

class SqlDelightKeyChainLinkRepository(
    private val db: KeysQueries,
    private val context: CoroutineContext
) : KeyChainLinkRepository {
    override suspend fun save(keyChainLink: KeyChainLink) = withContext(context) {
        db.saveKeyChainLink(
            Sql_key_chain_link(
                signing_user_id = keyChainLink.signingUserId.full,
                signing_key_id = keyChainLink.signingKey.keyId ?: "",
                signing_key_value = keyChainLink.signingKey.value,
                signed_user_id = keyChainLink.signedUserId.full,
                signed_key_id = keyChainLink.signedKey.keyId ?: "",
                signed_key_value = keyChainLink.signedKey.value
            )
        )
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        withContext(context) {
            db.getKeyChainLinkBySigningKey(
                signing_user_id = signingUserId.full,
                signing_key_id = signingKey.keyId ?: "",
                signing_key_value = signingKey.value
            ).executeAsList().map {
                KeyChainLink(
                    signingUserId = UserId(it.signing_user_id),
                    signingKey = Key.Ed25519Key(it.signing_key_id, it.signing_key_value),
                    signedUserId = UserId(it.signed_user_id),
                    signedKey = Key.Ed25519Key(it.signed_key_id, it.signed_key_value)
                )
            }.toSet()
        }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) = withContext(context) {
        db.deleteKeyChainLinkBySignedgKey(
            signed_user_id = signedUserId.full,
            signed_key_id = signedKey.keyId ?: "",
            signed_key_value = signedKey.value
        )
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllKeyChainLinks()
    }
}