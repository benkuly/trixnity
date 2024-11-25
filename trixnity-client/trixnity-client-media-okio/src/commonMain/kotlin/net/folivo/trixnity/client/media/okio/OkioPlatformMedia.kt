package net.folivo.trixnity.client.media.okio

import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.utils.ByteArrayFlow
import okio.Path

interface OkioPlatformMedia : PlatformMedia {
    override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OkioPlatformMedia
    suspend fun getTemporaryFile(): Result<TemporaryFile>

    interface TemporaryFile {
        val path: Path
        suspend fun release()
    }
}