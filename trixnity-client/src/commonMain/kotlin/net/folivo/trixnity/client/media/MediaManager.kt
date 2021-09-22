package net.folivo.trixnity.client.media

import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.media.FileTransferProgress
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.client.crypto.Aes256CtrInfo
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.crypto.decryptAes256Ctr
import net.folivo.trixnity.client.crypto.encryptAes256Ctr
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class MediaManager(
    private val api: MatrixApiClient,
    private val store: Store,
    loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    suspend fun getMedia(
        mxcUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): ByteArray {
        return store.media.byUri(mxcUri)
            ?: api.media.download(mxcUri, progress = progress).content.toByteArray().also { mediaDownload ->
                store.media.add(mxcUri, mediaDownload)
            }
    }

    suspend fun getEncryptedMedia(
        encryptedFile: EncryptedFile,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): ByteArray {
        val media = getMedia(encryptedFile.url, progress)
        val hash = freeAfter(OlmUtility.create()) {
            it.sha256(media)
        }
        val originalHash = encryptedFile.hashes["sha256"]
        if (originalHash == null || hash != originalHash) {
            log.debug { "could not validate due to different hashes. Our hash: $hash their hash: $originalHash" }
            throw DecryptionException.ValidationFailed
        }
        return decryptAes256Ctr(
            Aes256CtrInfo(
                encryptedContent = media,
                initialisationVector = encryptedFile.initialisationVector.decodeUnpaddedBase64Bytes(),
                // url-safe base64 is given
                key = encryptedFile.key.key.replace("-", "+").replace("_", "/")
                    .decodeUnpaddedBase64Bytes()
            )
        )
    }

    suspend fun getThumbnail(
        mxcUri: String,
        width: UInt,
        height: UInt,
        method: ThumbnailResizingMethod = CROP,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): ByteArray {
        val thumbnailUrl = "$mxcUri/${width}x$height/${method.value}"
        return store.media.byUri(thumbnailUrl)
            ?: api.media.downloadThumbnail(mxcUri, width, height, method, progress = progress).content.toByteArray()
                .also { mediaDownload ->
                    store.media.add(thumbnailUrl, mediaDownload)
                }
    }

    suspend fun uploadMedia(
        content: ByteArray,
        contentType: ContentType,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): String {
        val mxcUri = api.media.upload(
            content = ByteReadChannel(content),
            contentLength = content.size.toLong(),
            contentType = contentType,
            progress = progress
        ).contentUri
        store.media.add(mxcUri, content)
        return mxcUri
    }

    suspend fun uploadEncryptedMedia(
        content: ByteArray,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): EncryptedFile {
        val encrypted = encryptAes256Ctr(content)
        val mxcUri = uploadMedia(encrypted.encryptedContent, ContentType.Application.OctetStream, progress)
        val hash = freeAfter(OlmUtility.create()) {
            it.sha256(encrypted.encryptedContent)
        }
        return EncryptedFile(
            url = mxcUri,
            key = EncryptedFile.JWK(
                // url-safe base64 is required
                key = encrypted.key.encodeUnpaddedBase64().replace("+", "-").replace("/", "_")
            ),
            initialisationVector = encrypted.initialisationVector.encodeUnpaddedBase64(),
            hashes = mapOf("sha256" to hash)
        )
    }
}