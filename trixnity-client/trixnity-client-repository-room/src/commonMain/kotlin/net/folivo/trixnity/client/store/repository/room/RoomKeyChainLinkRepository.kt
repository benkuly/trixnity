package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

@Entity(
    tableName = "KeyChainLink",
    primaryKeys = [
        "signingUserId",
        "signingKeyId",
        "signingKeyValue",
        "signedUserId",
        "signedKeyId",
        "signedKeyValue",
    ]
)
data class RoomKeyChainLink(
    val signingUserId: UserId,
    val signingKeyId: String,
    val signingKeyValue: String,
    val signedUserId: UserId,
    val signedKeyId: String,
    val signedKeyValue: String,
)

@Dao
interface KeyChainLinkDao {
    @Query(
        """
        SELECT * FROM KeyChainLink
        WHERE signingUserId = :signingUserId
        AND signingKeyId = :signingKeyId
        AND signingKeyValue = :signingKeyValue
        """
    )
    suspend fun getBySigningKeys(
        signingUserId: UserId,
        signingKeyId: String?,
        signingKeyValue: String,
    ): List<RoomKeyChainLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomKeyChainLink)

    @Query(
        """
        DELETE FROM KeyChainLink
        WHERE signedUserId = :signedUserId
        AND signedKeyId = :signedKeyId
        AND signedKeyValue = :signedKeyValue
        """
    )
    suspend fun delete(
        signedUserId: UserId,
        signedKeyId: String?,
        signedKeyValue: String,
    ): Int

    @Query("DELETE FROM KeyChainLink")
    suspend fun deleteAll()
}

internal class RoomKeyChainLinkRepository(
    db: TrixnityRoomDatabase,
) : KeyChainLinkRepository {
    private val dao = db.keyChainLink()

    override suspend fun getBySigningKey(
        signingUserId: UserId,
        signingKey: Key.Ed25519Key
    ): Set<KeyChainLink> = withRoomRead {
        dao.getBySigningKeys(signingUserId, signingKey.id, signingKey.value.value)
            .map { entity ->
                KeyChainLink(
                    signingUserId = entity.signingUserId,
                    signingKey = Key.Ed25519Key(
                        id = entity.signingKeyId,
                        value = entity.signingKeyValue,
                    ),
                    signedUserId = entity.signedUserId,
                    signedKey = Key.Ed25519Key(
                        id = entity.signedKeyId,
                        value = entity.signedKeyValue,
                    ),
                )
            }.toSet()
    }

    override suspend fun save(keyChainLink: KeyChainLink) = withRoomWrite {
        dao.insert(
            RoomKeyChainLink(
                signingUserId = keyChainLink.signingUserId,
                signingKeyId = keyChainLink.signingKey.id ?: "",
                signingKeyValue = keyChainLink.signingKey.value.value,
                signedUserId = keyChainLink.signedUserId,
                signedKeyId = keyChainLink.signedKey.id ?: "",
                signedKeyValue = keyChainLink.signedKey.value.value,
            )
        )
    }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key) = withRoomWrite {
        dao.delete(signedUserId, signedKey.id, signedKey.value.value)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
