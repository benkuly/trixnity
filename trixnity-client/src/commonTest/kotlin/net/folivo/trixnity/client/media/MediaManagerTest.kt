package net.folivo.trixnity.client.media

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.media.DownloadResponse
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.client.api.media.UploadResponse
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import org.kodein.log.LoggerFactory

class MediaManagerTest : ShouldSpec({
    val api: MatrixApiClient = mockk()
    val store: Store = InMemoryStore()
    val cut = MediaManager(api, store, LoggerFactory.default)

    val mxcUri = "mxc://example.com/abc"

    beforeTest {
        clearMocks(api)
        store.clear()
        coEvery { api.media.upload(any(), any(), any(), any(), any()) } returns UploadResponse(mxcUri)
    }
    context(MediaManager::getMedia.name) {
        should("prefer cache") {
            store.media.add(mxcUri, "test".encodeToByteArray())
            cut.getMedia(mxcUri).decodeToString() shouldBe "test"
            coVerify { api wasNot Called }
        }
        should("download and cache") {
            coEvery { api.media.download(any(), any()) } returns DownloadResponse(
                ByteReadChannel("test"), null, null, null
            )
            cut.getMedia(mxcUri).decodeToString() shouldBe "test"
            coVerify { api.media.download(mxcUri) }
            store.media.byUri(mxcUri)?.decodeToString() shouldBe "test"
        }
    }
    context(MediaManager::getEncryptedMedia.name) {
        val rawFile = "lQ/twg".decodeUnpaddedBase64Bytes()
        val encryptedFile = EncryptedFile(
            url = mxcUri,
            key = EncryptedFile.JWK(
                key = "BQ67pT94oS2ykjYwC63Xx9KoGNKrfRKJ3DyTaoEghWU"
            ),
            initialisationVector = "xVA1MF7mXZ8AAAAAAAAAAA",
            hashes = mapOf("sha256" to "Hk9NwPYLemjX/b6MMxpLKYn632NkYSFaBEoEvj4Fzo4")
        )
        should("prefer cache and decrypt") {
            store.media.add(mxcUri, rawFile)
            cut.getEncryptedMedia(encryptedFile).decodeToString() shouldBe "test"
        }
        should("download, cache and decrypt") {
            coEvery { api.media.download(any(), any()) } returns DownloadResponse(
                ByteReadChannel(rawFile), null, null, null
            )
            cut.getEncryptedMedia(encryptedFile).decodeToString() shouldBe "test"
            store.media.byUri(mxcUri) shouldBe rawFile
        }
        should("validate hash") {
            store.media.add(mxcUri, rawFile)
            val encryptedFileWIthWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
            shouldThrow<DecryptionException.ValidationFailed> {
                cut.getEncryptedMedia(encryptedFileWIthWrongHash).decodeToString()
            }
        }
    }
    context(MediaManager::getThumbnail.name) {
        should("prefer cache") {
            store.media.add("$mxcUri/32x32/crop", "test".encodeToByteArray())
            cut.getThumbnail(mxcUri, 32u, 32u).decodeToString() shouldBe "test"
            coVerify { api wasNot Called }
        }
        should("download and cache") {
            coEvery { api.media.downloadThumbnail(mxcUri, 32u, 32u, CROP) } returns DownloadResponse(
                ByteReadChannel("test"), null, null, null
            )
            cut.getThumbnail(mxcUri, 32u, 32u).decodeToString() shouldBe "test"
            coVerify { api.media.downloadThumbnail(mxcUri, 32u, 32u, CROP) }
            store.media.byUri("$mxcUri/32x32/crop")?.decodeToString() shouldBe "test"
        }
    }
    context(MediaManager::uploadMedia.name) {
        should("upload and add to cache") {
            coEvery { api.media.upload(any(), any(), contentType = ContentType.Text.Plain) } returns UploadResponse(
                mxcUri
            )
            cut.uploadMedia("test".encodeToByteArray(), ContentType.Text.Plain) shouldBe mxcUri
            store.media.byUri(mxcUri)?.decodeToString() shouldBe "test"
        }
    }
    context(MediaManager::uploadEncryptedMedia.name) {
        should("encrypt, upload and add to cache") {
            coEvery {
                api.media.upload(
                    any(),
                    any(),
                    contentType = ContentType.Application.OctetStream
                )
            } returns UploadResponse(mxcUri)
            val result = cut.uploadEncryptedMedia("test".encodeToByteArray())
            assertSoftly(result) {
                url shouldBe mxcUri
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            store.media.byUri(mxcUri)?.decodeToString() shouldNotBe "test"
        }
    }
})