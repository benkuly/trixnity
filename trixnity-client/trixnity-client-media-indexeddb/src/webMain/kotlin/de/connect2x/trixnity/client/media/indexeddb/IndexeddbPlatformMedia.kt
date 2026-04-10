package de.connect2x.trixnity.client.media.indexeddb

import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.utils.ByteArrayFlow
import web.blob.Blob

interface IndexeddbPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia
    override suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile : PlatformMedia.TemporaryFile {
        val file: Blob
    }
}
