package net.folivo.trixnity.client.store.exposed

import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import org.jetbrains.exposed.sql.*

internal object ExposedKeyChainLink : Table("key_chain_link") {
    val signingUserId = varchar("signing_user_id", length = 65535)
    val signingKeyId = varchar("signing_key_id", length = 65535)
    val signingKeyValue = varchar("signing_key_value", length = 65535)
    val signedUserId = varchar("signed_user_id", length = 65535)
    val signedKeyId = varchar("signed_key_id", length = 65535)
    val signedKeyValue = varchar("signed_key_value", length = 65535)
    override val primaryKey = PrimaryKey(
        signingUserId, signingKeyId, signingKeyValue, signedUserId, signedKeyId, signedKeyValue
    )
}

internal class ExposedKeyChainLinkRepository : KeyChainLinkRepository {
    override suspend fun save(keyChainLink: KeyChainLink) {
        ExposedKeyChainLink.replace {
            it[signingUserId] = keyChainLink.signingUserId.full
            it[signingKeyId] = keyChainLink.signingKey.keyId ?: ""
            it[signingKeyValue] = keyChainLink.signingKey.value
            it[signedUserId] = keyChainLink.signedUserId.full
            it[signedKeyId] = keyChainLink.signedKey.keyId ?: ""
            it[signedKeyValue] = keyChainLink.signedKey.value
        }
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> {
        return ExposedKeyChainLink.select {
            ExposedKeyChainLink.signingUserId.eq(signingUserId.full) and
                    ExposedKeyChainLink.signingKeyId.eq(signingKey.keyId ?: "") and
                    ExposedKeyChainLink.signingKeyValue.eq(signingKey.value)
        }.map {
            KeyChainLink(
                signingUserId = UserId(it[ExposedKeyChainLink.signingUserId]),
                signingKey = Key.Ed25519Key(
                    it[ExposedKeyChainLink.signingKeyId],
                    it[ExposedKeyChainLink.signingKeyValue]
                ),
                signedUserId = UserId(it[ExposedKeyChainLink.signedUserId]),
                signedKey = Key.Ed25519Key(
                    it[ExposedKeyChainLink.signedKeyId],
                    it[ExposedKeyChainLink.signedKeyValue]
                ),
            )
        }.toSet()
    }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) {
        ExposedKeyChainLink.deleteWhere {
            ExposedKeyChainLink.signedUserId.eq(signedUserId.full) and
                    ExposedKeyChainLink.signedKeyId.eq(signedKey.keyId ?: "") and
                    ExposedKeyChainLink.signedKeyValue.eq(signedKey.value)
        }
    }
}