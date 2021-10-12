package net.folivo.trixnity.client.media

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.http.ContentType.Text.Plain
import io.ktor.utils.io.*
import io.mockk.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.media.DownloadResponse
import net.folivo.trixnity.client.api.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.client.api.media.UploadResponse
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.UploadMedia
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import org.kodein.log.LoggerFactory

class MediaManagerTest : ShouldSpec({
    val api: MatrixApiClient = mockk()
    val store = mockk<Store>(relaxUnitFun = true)
    val cut = MediaManager(api, store, LoggerFactory.default)

    val mxcUri = "mxc://example.com/abc"

    beforeTest {
        clearAllMocks()
        coEvery { api.media.upload(any(), any(), any(), any(), any()) } returns UploadResponse(mxcUri)
        coEvery { store.media.getContent(any()) } returns null
    }
    context(MediaManager::getMedia.name) {
        should("prefer cache") {
            coEvery { store.media.getContent(mxcUri) } returns "test".encodeToByteArray()
            cut.getMedia(mxcUri).decodeToString() shouldBe "test"
            coVerify { api wasNot Called }
        }
        should("download and cache") {
            coEvery { api.media.download(any(), any()) } returns DownloadResponse(
                ByteReadChannel("test"), null, null, null
            )
            cut.getMedia(mxcUri).decodeToString() shouldBe "test"

            coVerify {
                api.media.download(mxcUri)
                store.media.addContent(mxcUri, "test".encodeToByteArray())
            }
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
            coEvery { store.media.getContent(mxcUri) } returns rawFile
            cut.getEncryptedMedia(encryptedFile).decodeToString() shouldBe "test"
        }
        should("download, cache and decrypt") {
            coEvery { api.media.download(any(), any()) } returns DownloadResponse(
                ByteReadChannel(rawFile), null, null, null
            )
            cut.getEncryptedMedia(encryptedFile).decodeToString() shouldBe "test"
            coVerify { store.media.addContent(mxcUri, rawFile) }
        }
        should("validate hash") {
            coEvery { store.media.getContent(mxcUri) } returns rawFile
            val encryptedFileWIthWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
            shouldThrow<DecryptionException.ValidationFailed> {
                cut.getEncryptedMedia(encryptedFileWIthWrongHash).decodeToString()
            }
        }
    }
    context(MediaManager::getThumbnail.name) {
        should("prefer cache") {
            coEvery { store.media.getContent("$mxcUri/32x32/crop") } returns "test".encodeToByteArray()
            cut.getThumbnail(mxcUri, 32u, 32u).decodeToString() shouldBe "test"
            coVerify { api wasNot Called }
        }
        should("download and cache") {
            coEvery { api.media.downloadThumbnail(mxcUri, 32u, 32u, CROP) } returns DownloadResponse(
                ByteReadChannel("test"), null, null, null
            )
            cut.getThumbnail(mxcUri, 32u, 32u).decodeToString() shouldBe "test"
            coVerify {
                api.media.downloadThumbnail(mxcUri, 32u, 32u, CROP)
                store.media.addContent("$mxcUri/32x32/crop", "test".encodeToByteArray())
            }
        }
    }
    context(MediaManager::prepareUploadMedia.name) {
        should("save and return local cache uri") {
            val result = cut.prepareUploadMedia("test".encodeToByteArray(), Plain)
            result shouldStartWith MediaManager.UPLOAD_MEDIA_CACHE_URI_PREFIX
            result.length shouldBeGreaterThan 12
            coVerify {
                store.media.addContent(result, "test".encodeToByteArray())
                store.media.updateUploadMedia(result, coWithArg {
                    it.invoke(null) shouldBe UploadMedia(result, null, Plain)
                })
            }
        }
    }
    context(MediaManager::prepareUploadEncryptedMedia.name) {
        should("encrypt, save, and return local cache uri") {
            val result = cut.prepareUploadEncryptedMedia("test".encodeToByteArray())
            assertSoftly(result) {
                url shouldStartWith MediaManager.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            coVerify {
                store.media.addContent(result.url, withArg {
                    it shouldNotBe "test".encodeToByteArray()
                })
                store.media.updateUploadMedia(result.url, coWithArg {
                    it.invoke(null) shouldBe UploadMedia(result.url, null, OctetStream)
                })
            }
        }
    }
    context(MediaManager::uploadMedia.name) {
        should("upload and add to cache") {
            coEvery { api.media.upload(any(), any(), contentType = Plain) } returns UploadResponse(mxcUri)
            val cacheUri = "cache://some-uuid"
            coEvery { store.media.getContent(cacheUri) } returns "test".encodeToByteArray()
            coEvery { store.media.getUploadMedia(cacheUri) } returns UploadMedia(cacheUri, null, Plain)

            cut.uploadMedia(cacheUri) shouldBe mxcUri

            coVerify {
                api.media.upload(any(), any(), any())
                store.media.changeUri(cacheUri, mxcUri)
                store.media.updateUploadMedia(cacheUri, coWithArg {
                    it.invoke(UploadMedia(cacheUri, null, Plain)) shouldBe UploadMedia(cacheUri, mxcUri, Plain)
                })
            }
        }
        should("should not upload twice") {
            coEvery { api.media.upload(any(), any(), contentType = Plain) } returns UploadResponse(mxcUri)
            val cacheUri = "cache://some-uuid"
            coEvery { store.media.getContent(cacheUri) } returns "test".encodeToByteArray()
            coEvery { store.media.getUploadMedia(cacheUri) }
                .returns(UploadMedia(cacheUri, null, Plain))
                .andThen(UploadMedia(cacheUri, mxcUri, Plain))

            cut.uploadMedia(cacheUri) shouldBe mxcUri
            cut.uploadMedia(cacheUri) shouldBe mxcUri

            coVerify(exactly = 1) { api.media.upload(any(), any(), any()) }
        }
    }
})