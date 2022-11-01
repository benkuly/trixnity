package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

internal class RealmKeyChainLink : RealmObject {
    var signingUserId: String = ""
    var signingKeyId: String = ""
    var signingKeyValue: String = ""
    var signedUserId: String = ""
    var signedKeyId: String = ""
    var signedKeyValue: String = ""
}

internal class RealmKeyChainLinkRepository : KeyChainLinkRepository {
    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        withRealmRead {
            findBySigningKeys(signingUserId, signingKey).map {
                KeyChainLink(
                    signingUserId = UserId(it.signingUserId),
                    signingKey = Key.Ed25519Key(
                        it.signingKeyId,
                        it.signingKeyValue,
                    ),
                    signedUserId = UserId(it.signedUserId),
                    signedKey = Key.Ed25519Key(
                        it.signedKeyId,
                        it.signedKeyValue,
                    )
                )
            }.toSet()
        }

    override suspend fun save(keyChainLink: KeyChainLink) = withRealmWrite {
        val existing = findByKey(keyChainLink).find()
        val upsert = (existing ?: RealmKeyChainLink()).apply {
            signingUserId = keyChainLink.signingUserId.full
            signingKeyId = keyChainLink.signingKey.keyId ?: ""
            signingKeyValue = keyChainLink.signingKey.value
            signedUserId = keyChainLink.signedUserId.full
            signedKeyId = keyChainLink.signedKey.keyId ?: ""
            signedKeyValue = keyChainLink.signedKey.value
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
    }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) = withRealmWrite {
        val existing = findBySignedKeys(signedUserId, signedKey)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmKeyChainLink>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(keyChainLink: KeyChainLink) = query<RealmKeyChainLink>(
        "signingUserId == $0 && signingKeyId == $1 && signingKeyValue == $2 && signedUserId == $3 && signedKeyId == $4 && signedKeyValue == $5",
        keyChainLink.signedUserId.full,
        keyChainLink.signingKey.keyId,
        keyChainLink.signingKey.value,
        keyChainLink.signedUserId.full,
        keyChainLink.signedKey.keyId,
        keyChainLink.signedKey.value,
    ).first()

    private fun TypedRealm.findBySigningKeys(
        signingUserId: UserId,
        signingKey: Key.Ed25519Key
    ) = query<RealmKeyChainLink>(
        "signingUserId == $0 && signingKeyId == $1 && signingKeyValue == $2",
        signingUserId.full,
        signingKey.keyId,
        signingKey.value
    ).find()

    private fun TypedRealm.findBySignedKeys(
        signingUserId: UserId,
        signingKey: Key.Ed25519Key
    ) = query<RealmKeyChainLink>(
        "signedUserId == $0 && signedKeyId == $1 && signedKeyValue == $2",
        signingUserId.full,
        signingKey.keyId,
        signingKey.value
    ).find()
}