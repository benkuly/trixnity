package net.folivo.trixnity.client.media.indexeddb

import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.utils.ByteArrayFlow
import web.blob.Blob

interface IndexeddbPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia
    suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile {
        val file: Blob
        suspend fun delete()
    }
}