package net.folivo.trixnity.client.media

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.http.ContentType.Image.PNG
import io.ktor.http.ContentType.Text.Plain
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.getInMemoryMediaStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.MediaStore
import net.folivo.trixnity.client.store.UploadCache
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.testutils.PortableMockEngineConfig

class MediaServiceTest : ShouldSpec({
    timeout = 60_000

    lateinit var mediaStore: MediaStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: MediaService

    val mxcUri = "mxc://example.com/abc"
    val cacheUri = "cache://some-string"

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        mediaStore = getInMemoryMediaStore(scope)
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = MediaService(api, mediaStore)
    }
    afterTest {
        scope.cancel()
    }
    context(MediaService::getMedia.name) {
        context("is mxc uri") {
            should("prefer cache") {
                mediaStore.addContent(mxcUri, "test".encodeToByteArray())
                cut.getMedia(mxcUri).getOrThrow().decodeToString() shouldBe "test"
            }
            should("download and cache") {
                apiConfig.endpoints {
                    addHandler {
                        it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                        respond(ByteReadChannel("test"), HttpStatusCode.OK)
                    }
                }
                cut.getMedia(mxcUri).getOrThrow().decodeToString() shouldBe "test"

                mediaStore.getContent(mxcUri) shouldBe "test".encodeToByteArray()
            }
        }
        context("is cache uri") {
            should("prefer cache") {
                mediaStore.addContent(cacheUri, "test".encodeToByteArray())
                cut.getMedia(cacheUri).getOrThrow().decodeToString() shouldBe "test"
            }
            should("prefer cache, but use mxcUri, when already uploaded") {
                mediaStore.updateUploadCache(cacheUri) { UploadCache(cacheUri, mxcUri) }
                mediaStore.addContent(mxcUri, "test".encodeToByteArray())
                cut.getMedia(cacheUri).getOrThrow().decodeToString() shouldBe "test"
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
            mediaStore.addContent(mxcUri, rawFile)
            cut.getEncryptedMedia(encryptedFile).getOrThrow().decodeToString() shouldBe "test"
        }
        should("download, cache and decrypt") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                    respond(rawFile, HttpStatusCode.OK)
                }
            }
            cut.getEncryptedMedia(encryptedFile).getOrThrow().decodeToString() shouldBe "test"
            mediaStore.getContent(mxcUri) shouldBe rawFile
        }
        should("validate hash") {
            mediaStore.addContent(mxcUri, rawFile)
            val encryptedFileWIthWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
            shouldThrow<DecryptionException.ValidationFailed> {
                cut.getEncryptedMedia(encryptedFileWIthWrongHash).getOrThrow().decodeToString()
            }
        }
    }
    context(MediaService::getThumbnail.name) {
        should("prefer cache") {
            mediaStore.addContent("$mxcUri/32x32/crop", "test".encodeToByteArray())
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().decodeToString() shouldBe "test"
        }
        should("download and cache") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/thumbnail/example.com/abc"
                    respond(ByteReadChannel("test"), HttpStatusCode.OK)
                }
            }
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().decodeToString() shouldBe "test"
            mediaStore.getContent("$mxcUri/32x32/crop") shouldBe "test".encodeToByteArray()
        }
    }
    context(MediaService::prepareUploadMedia.name) {
        should("save and return local cache uri from media") {
            val result = cut.prepareUploadMedia("test".encodeToByteArray(), Plain)
            result shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
            result.length shouldBeGreaterThan 12
            mediaStore.getContent(result) shouldBe "test".encodeToByteArray()
            mediaStore.getUploadCache(result) shouldBe UploadCache(result, null, Plain.toString())
        }
    }
    context(MediaService::prepareUploadThumbnail.name) {
        should("save and return local cache uri from thumbnail") {
            val result = cut.prepareUploadThumbnail(miniPng, PNG)
            result?.first shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
            assertSoftly(result.shouldNotBeNull().second) {
                width shouldBe 600
                height shouldBe 600
                size.shouldNotBeNull() shouldBeGreaterThan 1000
                mimeType shouldBe "image/png"
            }
            mediaStore.getContent(result.first).shouldNotBeNull().size shouldBeGreaterThan 24
            mediaStore.getUploadCache(result.first) shouldBe UploadCache(result.first, null, PNG.toString())
        }
        should("return null, when no thumbnail could be generated") {
            cut.prepareUploadThumbnail("test".toByteArray(), PNG) shouldBe null
        }
    }
    context(MediaService::prepareUploadEncryptedMedia.name) {
        should("encrypt, save, and return local cache uri from media") {
            val result = cut.prepareUploadEncryptedMedia("test".encodeToByteArray())
            assertSoftly(result) {
                url shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            mediaStore.getContent(result.url) shouldNotBe "test".encodeToByteArray()
            mediaStore.getUploadCache(result.url) shouldBe UploadCache(result.url, null, OctetStream.toString())
        }
    }
    context(MediaService::prepareUploadEncryptedThumbnail.name) {
        should("encrypt, save, and return local cache uri from thumbnail") {
            val result = cut.prepareUploadEncryptedThumbnail(miniPng, PNG)
            assertSoftly(result.shouldNotBeNull().first) {
                url shouldStartWith MediaService.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            assertSoftly(result.second) {
                width shouldBe 600
                height shouldBe 600
                size.shouldNotBeNull() shouldBeGreaterThan 1000
                mimeType shouldBe "image/png"
            }
            mediaStore.getContent(result.first.url).shouldNotBeNull().size shouldBeGreaterThan 24
            mediaStore.getUploadCache(result.first.url) shouldBe UploadCache(
                result.first.url,
                null,
                OctetStream.toString()
            )
        }
        should("return null, when no encrypted thumbnail could be generated") {
            cut.prepareUploadEncryptedThumbnail("test".toByteArray(), PNG) shouldBe null
        }
    }
    context(MediaService::uploadMedia.name) {
        should("upload and add to cache") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                    respond(
                        """{"content_uri":"$mxcUri"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                    )
                }
            }
            mediaStore.addContent(cacheUri, "test".encodeToByteArray())
            mediaStore.updateUploadCache(cacheUri) { UploadCache(cacheUri, null, Plain.toString()) }

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            mediaStore.getUploadCache(cacheUri) shouldBe UploadCache(cacheUri, mxcUri, Plain.toString())
            // we cannot check this, because the value will stay in cache
            // mediaStore.getContent(cacheUri) shouldBe null
            mediaStore.getContent(mxcUri) shouldBe "test".encodeToByteArray()
        }
        should("not upload twice") {
            var calledCount = 0
            apiConfig.endpoints {
                addHandler {
                    calledCount++
                    respond(
                        """{"content_uri":"$mxcUri"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                    )
                }
            }
            mediaStore.addContent(cacheUri, "test".encodeToByteArray())
            mediaStore.updateUploadCache(cacheUri) { UploadCache(cacheUri, null, Plain.toString()) }

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri
            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            calledCount shouldBe 1
        }
    }
})