package net.folivo.trixnity.client.store.exposed

import net.folivo.trixnity.client.store.repository.MediaRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

internal object ExposedMedia : Table("media") {
    val url = varchar("url", length = 65535)
    override val primaryKey = PrimaryKey(url)
    val media = blob("media")
}

internal class ExposedMediaRepository : MediaRepository {
    override suspend fun get(key: String): ByteArray? {
        return ExposedMedia.select { ExposedMedia.url eq key }.firstOrNull()?.let {
            it[ExposedMedia.media].bytes
        }
    }

    override suspend fun save(key: String, value: ByteArray) {
        ExposedMedia.replace {
            it[url] = key
            it[media] = ExposedBlob(value)
        }
    }

    override suspend fun changeUri(oldUri: String, newUri: String) {
        ExposedMedia.update({ ExposedMedia.url eq oldUri }) {
            it[url] = newUri
        }
    }

    override suspend fun delete(key: String) {
        ExposedMedia.deleteWhere { ExposedMedia.url eq key }
    }

    override suspend fun deleteAll() {
        ExposedMedia.deleteAll()
    }
}