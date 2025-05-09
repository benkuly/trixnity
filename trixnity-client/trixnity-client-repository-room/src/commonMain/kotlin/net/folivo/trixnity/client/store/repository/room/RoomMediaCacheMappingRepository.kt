package net.folivo.trixnity.client.store.repository.room

import androidx.room.*
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository

@Entity(tableName = "MediaCacheMapping")
data class RoomMediaCacheMapping(
    @PrimaryKey val cacheUri: String,
    val mxcUri: String?,
    val size: Long,
    val contentType: String?,
)

@Dao
interface MediaCacheMappingDao {
    @Query("SELECT * FROM MediaCacheMapping WHERE cacheUri = :cacheUri LIMIT 1")
    suspend fun get(cacheUri: String): RoomMediaCacheMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RoomMediaCacheMapping)

    @Query("DELETE FROM MediaCacheMapping WHERE cacheUri = :cacheUri")
    suspend fun delete(cacheUri: String)

    @Query("DELETE FROM MediaCacheMapping")
    suspend fun deleteAll()
}

internal class RoomMediaCacheMappingRepository(
    db: TrixnityRoomDatabase,
) : MediaCacheMappingRepository {
    private val dao = db.mediaCacheMapping()

    override suspend fun get(key: String): MediaCacheMapping? = withRoomRead {
        dao.get(key)?.let { entity ->
            MediaCacheMapping(
                cacheUri = entity.cacheUri,
                mxcUri = entity.mxcUri,
                size = entity.size,
                contentType = entity.contentType,
            )
        }
    }

    override suspend fun save(key: String, value: MediaCacheMapping) = withRoomWrite {
        dao.insert(
            RoomMediaCacheMapping(
                cacheUri = value.cacheUri,
                mxcUri = value.mxcUri,
                size = value.size,
                contentType = value.contentType,
            )
        )
    }

    override suspend fun delete(key: String) = withRoomWrite {
        dao.delete(cacheUri = key)
    }

    override suspend fun deleteAll() = withRoomWrite {
        dao.deleteAll()
    }
}
