package de.connect2x.trixnity.client.media.opfs

import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.utils.ByteArrayFlow
import web.file.File

interface OpfsPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OpfsPlatformMedia
    override suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile : PlatformMedia.TemporaryFile {
        val file: File
    }
}
