package de.connect2x.trixnity.client.media.indexeddb

import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.utils.ByteArrayFlow
import web.blob.Blob

interface IndexeddbPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia
    suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile {
        val file: Blob
        suspend fun delete()
    }
}