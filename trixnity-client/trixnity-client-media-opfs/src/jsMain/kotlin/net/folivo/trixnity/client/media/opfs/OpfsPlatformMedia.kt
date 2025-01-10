package net.folivo.trixnity.client.media.opfs

import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.utils.ByteArrayFlow
import web.file.File

interface OpfsPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OpfsPlatformMedia
    suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile {
        val file: File
        suspend fun delete()
    }
}