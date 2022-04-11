package net.folivo.trixnity.client.store.exposed

import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.client.store.repository.UploadMediaRepository
import org.jetbrains.exposed.sql.*

internal object ExposedUploadMedia : Table("upload_media") {
    val cacheUri = varchar("cache_uri", length = 768)
    override val primaryKey = PrimaryKey(cacheUri)
    val mxcUri = text("mxc_uri").nullable()
    val contentType = text("content_type").nullable()
}

internal class ExposedUploadMediaRepository : UploadMediaRepository {
    override suspend fun get(key: String): UploadCache? {
        return ExposedUploadMedia.select { ExposedUploadMedia.cacheUri eq key }.firstOrNull()?.let {
            UploadCache(
                key,
                it[ExposedUploadMedia.mxcUri],
                it[ExposedUploadMedia.contentType]
            )
        }
    }

    override suspend fun save(key: String, value: UploadCache) {
        ExposedUploadMedia.replace {
            it[cacheUri] = key
            it[mxcUri] = value.mxcUri
            it[contentType] = value.contentType.toString()
        }
    }

    override suspend fun delete(key: String) {
        ExposedUploadMedia.deleteWhere { ExposedUploadMedia.cacheUri eq key }
    }

    override suspend fun deleteAll() {
        ExposedUploadMedia.deleteAll()
    }
}