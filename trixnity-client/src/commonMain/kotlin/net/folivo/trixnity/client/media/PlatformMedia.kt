package net.folivo.trixnity.client.media

import net.folivo.trixnity.utils.ByteArrayFlow

interface PlatformMedia : ByteArrayFlow {
    fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia
}