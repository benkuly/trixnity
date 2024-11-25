package net.folivo.trixnity.client.media

import net.folivo.trixnity.utils.ByteArrayFlow

/**
 * Depending on the configured [MediaStore] you may have access to more than the [ByteArrayFlow]. For example:
 *
 * ```kotlin
 * check(platformMedia is OpfsPlatformMedia)
 * platformMedia.getTemporaryFile()
 * ```
 */
interface PlatformMedia : ByteArrayFlow {
    fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia
}