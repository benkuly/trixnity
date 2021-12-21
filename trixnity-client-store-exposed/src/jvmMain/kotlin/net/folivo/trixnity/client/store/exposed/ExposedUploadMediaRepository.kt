package net.folivo.trixnity.client.store.exposed

import io.ktor.http.*
import net.folivo.trixnity.client.store.UploadMedia
import net.folivo.trixnity.client.store.repository.UploadMediaRepository
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedUploadMedia : Table("upload_media") {
    val cacheUri = varchar("cache_uri", length = 65535)
    override val primaryKey = PrimaryKey(cacheUri)
    val mxcUri = text("mxc_uri").nullable()
    val contentType = text("content_type").nullable()
}

internal class ExposedUploadMediaRepository : UploadMediaRepository {
    override suspend fun get(key: String): UploadMedia? {
        return ExposedUploadMedia.select { ExposedUploadMedia.cacheUri eq key }.firstOrNull()?.let {
            UploadMedia(
                key,
                it[ExposedUploadMedia.mxcUri],
                it[ExposedUploadMedia.contentType]?.let { it2 -> ContentType.parse(it2) })
        }
    }

    override suspend fun save(key: String, value: UploadMedia) {
        ExposedUploadMedia.replace {
            it[cacheUri] = key
            it[mxcUri] = value.mxcUri
            it[contentType] = value.contentTyp.toString()
        }
    }

    override suspend fun delete(key: String) {
        ExposedUploadMedia.deleteWhere { ExposedUploadMedia.cacheUri eq key }
    }
}