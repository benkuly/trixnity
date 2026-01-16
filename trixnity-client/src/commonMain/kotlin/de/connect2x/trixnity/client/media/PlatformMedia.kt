package de.connect2x.trixnity.client.media

import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.utils.ByteArrayFlow

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

    /**
     * Creates a [ByteArray] and stores it into a cache for re-use.
     *
     * @param coroutineScope The [CoroutineScope] is used to remove the [ByteArray] from cache when not used anymore.
     *      Otherwise, it is automatically removed after the media value defined in [MatrixClientConfiguration.cacheExpireDurations].
     * @param expectedSize The size of the media propagated by e.g., events or null if not known.
     * @param maxSize The max size allowed loading into memory.
     *
     * @return The loaded media as a [ByteArray] or null when [maxSize] was exceeded.
     */
    suspend fun toByteArray(
        coroutineScope: CoroutineScope? = null,
        expectedSize: Long? = null,
        maxSize: Long? = null
    ): ByteArray?
}