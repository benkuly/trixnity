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
import io.ktor.http.ContentType.Image.JPEG
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Text.Plain
import io.ktor.utils.io.*
import io.mockk.*
import net.folivo.trixnity.client.crypto.DecryptionException
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.media.ThumbnailResizingMethod.CROP
import net.folivo.trixnity.clientserverapi.model.media.UploadMedia
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes

class MediaServiceTest : ShouldSpec({
    val api: MatrixClientServerApiClient = mockk()
    val store = mockk<Store>(relaxUnitFun = true)
    val json = createMatrixJson()
    val cut = MediaService(api, store)

    val mxcUri = "mxc://example.com/abc"
    val cacheUri = "cache://some-string"

    mockkStatic(::createThumbnail)

    beforeTest {
        clearAllMocks()
        coEvery { api.media.upload(any(), any(), any()) } returns Result.success(
            UploadMedia.Response(mxcUri)
        )
        coEvery { api.json } returns json
        coEvery { store.media.getContent(any()) } returns null
    }
    context(MediaService::getMedia.name) {
        context("is mxc uri") {
            should("prefer cache") {
                coEvery { store.media.getContent(mxcUri) } returns "test".encodeToByteArray()
                cut.getMedia(mxcUri).getOrThrow().decodeToString() shouldBe "test"
                val mediaMock = api.media
                coVerify { mediaMock wasNot Called }
            }
            should("download and cache") {
                coEvery { api.media.download(any(), any()) } returns Result.success(
                    Media(ByteReadChannel("test"), null, null, null)
                )
                cut.getMedia(mxcUri).getOrThrow().decodeToString() shouldBe "test"

                coVerify {
                    api.media.download(mxcUri)
                    store.media.addContent(mxcUri, "test".encodeToByteArray())
                }
            }
        }
        context("is cache uri") {
            should("prefer cache") {
                coEvery { store.media.getContent(cacheUri) } returns "test".encodeToByteArray()
                cut.getMedia(cacheUri).getOrThrow().decodeToString() shouldBe "test"
                val mediaMock = api.media
                coVerify { mediaMock wasNot Called }
            }
            should("prefer cache, but use mxcUri, when already uploaded") {
                coEvery { store.media.getUploadCache(cacheUri)?.mxcUri } returns mxcUri
                coEvery { store.media.getContent(mxcUri) } returns "test".encodeToByteArray()
                cut.getMedia(cacheUri).getOrThrow().decodeToString() shouldBe "test"
                val mediaMock = api.media
                coVerify { mediaMock wasNot Called }
            }
        }
    }
    context(MediaService::getEncryptedMedia.name) {
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
            cut.getEncryptedMedia(encryptedFile).getOrThrow().decodeToString() shouldBe "test"
        }
        should("download, cache and decrypt") {
            coEvery { api.media.download(any(), any()) } returns Result.success(
                Media(ByteReadChannel(rawFile), null, null, null)
            )
            cut.getEncryptedMedia(encryptedFile).getOrThrow().decodeToString() shouldBe "test"
            coVerify { store.media.addContent(mxcUri, rawFile) }
        }
        should("validate hash") {
            coEvery { store.media.getContent(mxcUri) } returns rawFile
            val encryptedFileWIthWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
            shouldThrow<DecryptionException.ValidationFailed> {
                cut.getEncryptedMedia(encryptedFileWIthWrongHash).getOrThrow().decodeToString()
            }
        }
    }
    context(MediaService::getThumbnail.name) {
        should("prefer cache") {
            coEvery { store.media.getContent("$mxcUri/32x32/crop") } returns "test".encodeToByteArray()
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().decodeToString() shouldBe "test"
            val mediaMock = api.media
            coVerify { mediaMock wasNot Called }
        }
        should("download and cache") {
            coEvery { api.media.downloadThumbnail(mxcUri, 32, 32, CROP) } returns Result.success(
                Media(ByteReadChannel("test"), null, null, null)
            )
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().decodeToString() shouldBe "test"
            coVerify {
                api.media.downloadThumbnail(mxcUri, 32, 32, CROP)
                store.media.addContent("$mxcUri/32x32/crop", "test".encodeToByteArray())
            }
        }
    }
    context(MediaService::prepareUploadMedia.name) {
        should("save and return local cache uri") {
            val result = cut.prepareUploadMedia("test".encodeToByteArray(), Plain)
            result shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
            result.length shouldBeGreaterThan 12
            coVerify {
                store.media.addContent(result, "test".encodeToByteArray())
                store.media.updateUploadCache(result, coWithArg {
                    it.invoke(null) shouldBe UploadCache(result, null, Plain.toString())
                })
            }
        }
    }
    context(MediaService::prepareUploadThumbnail.name) {
        should("save and return local cache uri") {
            val file = "fake_file".encodeToByteArray()
            val thumbnail = "fake_thumbnail".encodeToByteArray()
            coEvery { createThumbnail(file, PNG, 600, 600) }
                .returns(Thumbnail(thumbnail, JPEG, 600, 300))

            val result = cut.prepareUploadThumbnail(file, PNG)
            result?.first shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
            assertSoftly(result!!.second) {
                width shouldBe 600
                height shouldBe 300
                size shouldBe 14
                mimeType shouldBe "image/jpeg"
            }
            coVerify {
                store.media.addContent(result.first, thumbnail)
                store.media.updateUploadCache(result.first, coWithArg {
                    it.invoke(null) shouldBe UploadCache(result.first, null, JPEG.toString())
                })
            }
        }
        should("return null, when no thumbnail could be generated") {
            coEvery { createThumbnail("test".toByteArray(), PNG, 600, 600) }
                .throws(ThumbnailCreationException(IllegalArgumentException("wrong format")))

            cut.prepareUploadThumbnail("test".toByteArray(), PNG) shouldBe null
        }
    }
    context(MediaService::prepareUploadEncryptedMedia.name) {
        should("encrypt, save, and return local cache uri") {
            val result = cut.prepareUploadEncryptedMedia("test".encodeToByteArray())
            assertSoftly(result) {
                url shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            coVerify {
                store.media.addContent(result.url, withArg {
                    it shouldNotBe "test".encodeToByteArray()
                })
                store.media.updateUploadCache(result.url, coWithArg {
                    it.invoke(null) shouldBe UploadCache(result.url, null, OctetStream.toString())
                })
            }
        }
    }
    context(MediaService::prepareUploadEncryptedThumbnail.name) {
        should("encrypt, save, and return local cache uri") {
            val file = "fake_file".encodeToByteArray()
            val thumbnail = "fake_thumbnail".encodeToByteArray()
            coEvery { createThumbnail(file, PNG, 600, 600) }
                .returns(Thumbnail(thumbnail, JPEG, 600, 300))

            val result = cut.prepareUploadEncryptedThumbnail(file, PNG)
            assertSoftly(result!!.first) {
                url shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            assertSoftly(result.second) {
                width shouldBe 600
                height shouldBe 300
                size shouldBe 14
                mimeType shouldBe "image/jpeg"
            }
            coVerify {
                store.media.addContent(result.first.url, withArg {
                    it shouldNotBe "test".encodeToByteArray()
                })
                store.media.updateUploadCache(result.first.url, coWithArg {
                    it.invoke(null) shouldBe UploadCache(result.first.url, null, OctetStream.toString())
                })
            }
        }
        should("return null, when no thumbnail could be generated") {
            coEvery { createThumbnail("test".toByteArray(), PNG, 600, 600) }
                .throws(ThumbnailCreationException(IllegalArgumentException("wrong format")))

            cut.prepareUploadEncryptedThumbnail("test".toByteArray(), PNG) shouldBe null
        }
    }
    context(MediaService::uploadMedia.name) {
        should("upload and add to cache") {
            coEvery {
                api.media.upload(
                    any(),
                    any(),
                    any()
                )
            } returns Result.success(UploadMedia.Response(mxcUri))
            coEvery { store.media.getContent(cacheUri) } returns "test".encodeToByteArray()
            coEvery { store.media.getUploadCache(cacheUri) } returns UploadCache(cacheUri, null, Plain.toString())

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            coVerify {
                api.media.upload(any(), any(), any())
                store.media.changeUri(cacheUri, mxcUri)
                store.media.updateUploadCache(cacheUri, coWithArg {
                    it.invoke(UploadCache(cacheUri, null, Plain.toString())) shouldBe UploadCache(
                        cacheUri,
                        mxcUri,
                        Plain.toString()
                    )
                })
            }
        }
        should("keep local cache url in cache") {

        }
        should("not upload twice") {
            coEvery {
                api.media.upload(
                    any(),
                    any(),
                    any()
                )
            } returns Result.success(UploadMedia.Response(mxcUri))
            coEvery { store.media.getContent(cacheUri) } returns "test".encodeToByteArray()
            coEvery { store.media.getUploadCache(cacheUri) }
                .returns(UploadCache(cacheUri, null, Plain.toString()))
                .andThen(UploadCache(cacheUri, mxcUri, Plain.toString()))

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri
            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            coVerify(exactly = 1) { api.media.upload(any(), any(), any()) }
        }
    }
})