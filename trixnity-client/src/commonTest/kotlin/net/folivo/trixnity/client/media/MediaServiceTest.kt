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
import kotlinx.coroutines.flow.count
import net.folivo.trixnity.client.getInMemoryMediaCacheMapping
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.MediaCacheMappingStore
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import net.folivo.trixnity.crypto.olm.DecryptionException
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import kotlin.test.assertNotNull

class MediaServiceTest : ShouldSpec({
    timeout = 60_000

    lateinit var mediaCacheMappingStore: MediaCacheMappingStore
    lateinit var mediaStore: InMemoryMediaStore
    lateinit var scope: CoroutineScope
    val json = createMatrixEventJson()
    lateinit var apiConfig: PortableMockEngineConfig

    lateinit var cut: MediaServiceImpl

    val mxcUri = "mxc://example.com/abc"
    val cacheUri = "upload://some-string"

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        mediaCacheMappingStore = getInMemoryMediaCacheMapping(scope)
        mediaStore = InMemoryMediaStore()
        val (api, newApiConfig) = mockMatrixClientServerApiClient(json)
        apiConfig = newApiConfig
        cut = MediaServiceImpl(api, mediaStore, mediaCacheMappingStore)
    }
    afterTest {
        scope.cancel()
    }
    context(MediaServiceImpl::getMedia.name) {
        context("is mxc uri") {
            should("prefer cache") {
                mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteFlow())
                cut.getMedia(mxcUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
            }
            should("download and cache") {
                apiConfig.endpoints {
                    addHandler {
                        it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                        respond(ByteReadChannel("test"), HttpStatusCode.OK)
                    }
                }
                cut.getMedia(mxcUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"

                mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
            }
        }
        context("is cache uri") {
            should("prefer cache") {
                mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteFlow())
                cut.getMedia(cacheUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
            }
            should("prefer cache, but use mxcUri, when already uploaded") {
                mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) { MediaCacheMapping(cacheUri, mxcUri) }
                mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteFlow())
                cut.getMedia(cacheUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
            }
        }
    }
    context(MediaServiceImpl::getEncryptedMedia.name) {
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
            mediaStore.addMedia(mxcUri, rawFile.toByteFlow())
            cut.getEncryptedMedia(encryptedFile).getOrThrow().toByteArray().decodeToString() shouldBe "test"
        }
        should("download, cache and decrypt") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                    respond(rawFile, HttpStatusCode.OK)
                }
            }
            cut.getEncryptedMedia(encryptedFile).getOrThrow().toByteArray().decodeToString() shouldBe "test"
            mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe rawFile
        }
        should("download, not cache and decrypt") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                    respond(rawFile, HttpStatusCode.OK)
                }
            }
            cut.getEncryptedMedia(encryptedFile, saveToCache = false).getOrThrow().toByteArray()
                .decodeToString() shouldBe "test"
            mediaStore.getMedia(mxcUri) shouldBe null
        }
        should("validate hash") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/download/example.com/abc"
                    respond(rawFile, HttpStatusCode.OK)
                }
            }
            val encryptedFileWithWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
            shouldThrow<DecryptionException.ValidationFailed> {
                cut.getEncryptedMedia(encryptedFileWithWrongHash).getOrThrow().toByteArray().decodeToString()
            }
            mediaStore.getMedia(mxcUri) shouldBe null
        }
    }
    context(MediaServiceImpl::getThumbnail.name) {
        should("prefer cache") {
            mediaStore.addMedia("$mxcUri/32x32/crop", "test".encodeToByteArray().toByteFlow())
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().toByteArray().decodeToString() shouldBe "test"
        }
        should("download and cache") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/thumbnail/example.com/abc"
                    respond(ByteReadChannel("test"), HttpStatusCode.OK)
                }
            }
            cut.getThumbnail(mxcUri, 32, 32).getOrThrow().toByteArray().decodeToString() shouldBe "test"
            println(mediaStore.media.value)
            mediaStore.getMedia("$mxcUri/32x32/crop")?.toByteArray() shouldBe "test".encodeToByteArray()
        }
    }
    context(MediaServiceImpl::prepareUploadMedia.name) {
        should("save and return local cache uri from media") {
            val result = cut.prepareUploadMedia("test".encodeToByteArray().toByteFlow(), Plain)
            result shouldStartWith MediaServiceImpl.UPLOAD_MEDIA_CACHE_URI_PREFIX
            result.length shouldBeGreaterThan 12
            mediaStore.getMedia(result)?.toByteArray() shouldBe "test".encodeToByteArray()
            mediaCacheMappingStore.getMediaCacheMapping(result) shouldBe
                    MediaCacheMapping(result, null, 4, Plain.toString())
        }
    }
    context(MediaServiceImpl::prepareUploadThumbnail.name) {
        should("save and return local cache uri from thumbnail") {
            val result = cut.prepareUploadThumbnail(miniPng.toByteFlow(), PNG)
            result?.first shouldStartWith MediaServiceImpl.UPLOAD_MEDIA_CACHE_URI_PREFIX
            assertSoftly(result.shouldNotBeNull().second) {
                width shouldBe 600
                height shouldBe 600
                size.shouldNotBeNull() shouldBeGreaterThan 1000
                mimeType shouldBe "image/png"
            }
            mediaStore.getMedia(result.first).shouldNotBeNull().count() shouldBeGreaterThan 24
            assertSoftly(mediaCacheMappingStore.getMediaCacheMapping(result.first)) {
                assertNotNull(this)
                this.cacheUri shouldBe result.first
                this.mxcUri shouldBe null
                this.size.shouldNotBeNull() shouldBeGreaterThan 0
                this.contentType shouldBe PNG.toString()
            }
        }
        should("return null, when no thumbnail could be generated") {
            cut.prepareUploadThumbnail("test".toByteArray().toByteFlow(), PNG) shouldBe null
        }
    }
    context(MediaServiceImpl::prepareUploadEncryptedMedia.name) {
        should("encrypt, save, and return local cache uri from media") {
            val result = cut.prepareUploadEncryptedMedia("test".encodeToByteArray().toByteFlow())
            assertSoftly(result) {
                url shouldStartWith MediaServiceImpl.UPLOAD_MEDIA_CACHE_URI_PREFIX
                url.length shouldBeGreaterThan 12
                key.key shouldNot beEmpty()
                initialisationVector shouldNot beEmpty()
                hashes["sha256"] shouldNot beEmpty()
            }
            mediaStore.getMedia(result.url)?.toByteArray() shouldNotBe "test".encodeToByteArray()
            mediaCacheMappingStore.getMediaCacheMapping(result.url) shouldBe MediaCacheMapping(
                result.url,
                null,
                4,
                OctetStream.toString()
            )
        }
    }
    context(MediaServiceImpl::prepareUploadEncryptedThumbnail.name) {
        should("encrypt, save, and return local cache uri from thumbnail") {
            val result = cut.prepareUploadEncryptedThumbnail(miniPng.toByteFlow(), PNG)
            assertSoftly(result.shouldNotBeNull().first) {
                url shouldStartWith MediaServiceImpl.UPLOAD_MEDIA_CACHE_URI_PREFIX
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
            mediaStore.getMedia(result.first.url).shouldNotBeNull().count() shouldBeGreaterThan 24
            assertSoftly(mediaCacheMappingStore.getMediaCacheMapping(result.first.url)) {
                assertNotNull(this)
                this.cacheUri shouldBe result.first.url
                this.mxcUri shouldBe null
                this.size.shouldNotBeNull() shouldBeGreaterThan 0
                this.contentType shouldBe OctetStream.toString()
            }
        }
        should("return null, when no encrypted thumbnail could be generated") {
            cut.prepareUploadEncryptedThumbnail("test".toByteArray().toByteFlow(), PNG) shouldBe null
        }
    }
    context(MediaServiceImpl::uploadMedia.name) {
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
            mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteFlow())
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, null, Plain.toString())
            }

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe MediaCacheMapping(
                cacheUri,
                mxcUri,
                null,
                Plain.toString()
            )
            mediaStore.getMedia(cacheUri) shouldBe null
            mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
        }
        should("upload and remove from cache after that") {
            apiConfig.endpoints {
                addHandler {
                    it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                    respond(
                        """{"content_uri":"$mxcUri"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                    )
                }
            }
            mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteFlow())
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, null, Plain.toString())
            }

            cut.uploadMedia(cacheUri, keepMediaInCache = false).getOrThrow() shouldBe mxcUri

            mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe null
            mediaStore.getMedia(cacheUri) shouldBe null
            mediaStore.getMedia(mxcUri) shouldBe null
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
            mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteFlow())
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, null, Plain.toString())
            }

            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri
            cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

            calledCount shouldBe 1
        }
    }
})