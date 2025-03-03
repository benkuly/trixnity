package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedKeyChainLink : Table("key_chain_link") {
    val signingUserId = varchar("signing_user_id", length = 128)
    val signingKeyId = varchar("signing_key_id", length = 128)
    val signingKeyValue = varchar("signing_key_value", length = 128)
    val signedUserId = varchar("signed_user_id", length = 128)
    val signedKeyId = varchar("signed_key_id", length = 128)
    val signedKeyValue = varchar("signed_key_value", length = 128)
    override val primaryKey = PrimaryKey(
        signingUserId, signingKeyId, signingKeyValue, signedUserId, signedKeyId, signedKeyValue
    )
}

internal class ExposedKeyChainLinkRepository : KeyChainLinkRepository {
    override suspend fun save(keyChainLink: KeyChainLink): Unit = withExposedWrite {
        ExposedKeyChainLink.upsert {
            it[signingUserId] = keyChainLink.signingUserId.full
            it[signingKeyId] = keyChainLink.signingKey.id ?: ""
            it[signingKeyValue] = keyChainLink.signingKey.value.value
            it[signedUserId] = keyChainLink.signedUserId.full
            it[signedKeyId] = keyChainLink.signedKey.id ?: ""
            it[signedKeyValue] = keyChainLink.signedKey.value.value
        }
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        withExposedRead {
            ExposedKeyChainLink.selectAll().where {
                ExposedKeyChainLink.signingUserId.eq(signingUserId.full) and
                        ExposedKeyChainLink.signingKeyId.eq(signingKey.id ?: "") and
                        ExposedKeyChainLink.signingKeyValue.eq(signingKey.value.value)
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

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key): Unit = withExposedWrite {
        ExposedKeyChainLink.deleteWhere {
            ExposedKeyChainLink.signedUserId.eq(signedUserId.full) and
                    signedKeyId.eq(signedKey.id ?: "") and
                    signedKeyValue.eq(signedKey.value.value)
        }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedKeyChainLink.deleteAll()
    }
}