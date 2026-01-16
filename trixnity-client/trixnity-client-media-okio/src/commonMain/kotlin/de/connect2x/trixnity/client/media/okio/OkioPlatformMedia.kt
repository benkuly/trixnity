package de.connect2x.trixnity.client.media.okio

import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.utils.ByteArrayFlow
import okio.Path

interface OkioPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OkioPlatformMedia
    suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile {
        val path: Path
        suspend fun delete()
    }
}