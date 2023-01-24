package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedMediaCacheMapping : Table("media_cache_mapping") {
    val cacheUri = varchar("cache_uri", length = 768)
    override val primaryKey = PrimaryKey(cacheUri)
    val mxcUri = text("mxc_uri").nullable()
    val size = integer("size").nullable()
    val contentType = text("content_type").nullable()
}

internal class ExposedMediaCacheMappingRepository : MediaCacheMappingRepository {
    override suspend fun get(key: String): MediaCacheMapping? = withExposedRead {
        ExposedMediaCacheMapping.select { ExposedMediaCacheMapping.cacheUri eq key }.firstOrNull()?.let {
            MediaCacheMapping(
                key,
                it[ExposedMediaCacheMapping.mxcUri],
                it[ExposedMediaCacheMapping.size],
                it[ExposedMediaCacheMapping.contentType]
            )
        }
    }

    override suspend fun save(key: String, value: MediaCacheMapping): Unit = withExposedWrite {
        ExposedMediaCacheMapping.replace {
            it[cacheUri] = key
            it[mxcUri] = value.mxcUri
            it[size] = value.size
            it[contentType] = value.contentType.toString()
        }
    }

    override suspend fun delete(key: String): Unit = withExposedWrite {
        ExposedMediaCacheMapping.deleteWhere { cacheUri eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedMediaCacheMapping.deleteAll()
    }
}