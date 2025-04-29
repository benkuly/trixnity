package net.folivo.trixnity.client.media

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.http.ContentType.Text.Plain
import io.ktor.utils.io.*
import net.folivo.trixnity.client.getInMemoryMediaCacheMapping
import net.folivo.trixnity.client.getInMemoryServerDataStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import kotlin.test.Test

class MediaServiceTest : TrixnityBaseTest() {

    private val mediaCacheMappingStore = getInMemoryMediaCacheMapping()
    private val serverDataStore = getInMemoryServerDataStore()
    private val mediaStore = InMemoryMediaStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = MediaServiceImpl(api, mediaStore, serverDataStore, mediaCacheMappingStore)

    private val mxcUri = "mxc://example.com/abc"
    private val cacheUri = "upload://some-string"

    @Test
    fun `getMedia » is mxc uri » prefer cache`() = runTest {
        mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(mxcUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
    }

    @Test
    fun `getMedia » is mxc uri » download and cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }
        cut.getMedia(mxcUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"

        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `getMedia » is cache uri » prefer cache`() = runTest {
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(cacheUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
    }

    @Test
    fun `getMedia » is cache uri » prefer cache but use mxcUri when already uploaded`() = runTest {
        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) { MediaCacheMapping(cacheUri, mxcUri, 4) }
        mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(cacheUri).getOrThrow().toByteArray().decodeToString() shouldBe "test"
    }

    private val rawFile = "lQ/twg".decodeUnpaddedBase64Bytes()
    private val encryptedFile = EncryptedFile(
        url = mxcUri,
        key = EncryptedFile.JWK(
            key = "BQ67pT94oS2ykjYwC63Xx9KoGNKrfRKJ3DyTaoEghWU"
        ),
        initialisationVector = "xVA1MF7mXZ8AAAAAAAAAAA",
        hashes = mapOf("sha256" to "Hk9NwPYLemjX/b6MMxpLKYn632NkYSFaBEoEvj4Fzo4")
    )

    @Test
    fun `getEncryptedMedia » prefer cache and decrypt`() = runTest {
        mediaStore.addMedia(mxcUri, rawFile.toByteArrayFlow())
        cut.getEncryptedMedia(encryptedFile).getOrThrow().toByteArray().decodeToString() shouldBe "test"
    }

    @Test
    fun `getEncryptedMedia » download cache and decrypt`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        cut.getEncryptedMedia(encryptedFile).getOrThrow().toByteArray().decodeToString() shouldBe "test"
        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe rawFile
    }

    @Test
    fun `getEncryptedMedia » download not cache and decrypt`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        cut.getEncryptedMedia(encryptedFile, saveToCache = false).getOrThrow().toByteArray()
            .decodeToString() shouldBe "test"
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `getEncryptedMedia » validate hash`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        val encryptedFileWithWrongHash = encryptedFile.copy(hashes = mapOf("sha256" to "nope"))
        shouldThrow<MediaValidationException> {
            cut.getEncryptedMedia(encryptedFileWithWrongHash).getOrThrow().toByteArray().decodeToString()
        }
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `getThumbnail » prefer cache`() = runTest {
        mediaStore.addMedia("$mxcUri/32x32/crop", "test".encodeToByteArray().toByteArrayFlow())
        cut.getThumbnail(mxcUri, 32, 32).getOrThrow().toByteArray().decodeToString() shouldBe "test"
    }

    @Test
    fun `getThumbnail » download and cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/thumbnail/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }
        cut.getThumbnail(mxcUri, 32, 32).getOrThrow().toByteArray().decodeToString() shouldBe "test"
        mediaStore.getMedia("$mxcUri/32x32/crop")?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `prepareUploadMedia » save and return local cache uri from media`() = runTest {
        val result = cut.prepareUploadMedia("test".encodeToByteArray().toByteArrayFlow(), Plain)
        result shouldStartWith MediaServiceImpl.UPLOAD_MEDIA_CACHE_URI_PREFIX
        result.length shouldBeGreaterThan 12
        mediaStore.getMedia(result)?.toByteArray() shouldBe "test".encodeToByteArray()
        mediaCacheMappingStore.getMediaCacheMapping(result) shouldBe
                MediaCacheMapping(result, null, 4, Plain.toString())
    }

    @Test
    fun `prepareUploadEncryptedMedia » encrypt save and return local cache uri from media`() = runTest {
        val result = cut.prepareUploadEncryptedMedia("test".encodeToByteArray().toByteArrayFlow())
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

    @Test
    fun `uploadMedia » upload and add to cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                respond(
                    """{"content_uri":"$mxcUri"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                )
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
            MediaCacheMapping(cacheUri, null, 4, Plain.toString())
        }

        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

        mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe MediaCacheMapping(
            cacheUri,
            mxcUri,
            4,
            Plain.toString()
        )
        mediaStore.getMedia(cacheUri) shouldBe null
        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `uploadMedia » upload and remove from cache after that`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                respond(
                    """{"content_uri":"$mxcUri"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                )
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
            MediaCacheMapping(cacheUri, null, 4, Plain.toString())
        }

        cut.uploadMedia(cacheUri, keepMediaInCache = false).getOrThrow() shouldBe mxcUri

        mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe null
        mediaStore.getMedia(cacheUri) shouldBe null
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `uploadMedia » not upload twice`() = runTest {
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
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
            MediaCacheMapping(cacheUri, null, 4, Plain.toString())
        }

        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri
        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

        calledCount shouldBe 1
    }

    @Test
    fun `uploadMedia » contain exception when file too large`() = runTest {
        val oldServerData = serverDataStore.getServerData()
        serverDataStore.setServerData(oldServerData.copy(mediaConfig = oldServerData.mediaConfig.copy(maxUploadSize = 4)))
        mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
            MediaCacheMapping(cacheUri, null, 5, Plain.toString())
        }
        shouldThrow<MediaTooLargeException> {
            cut.uploadMedia(cacheUri).getOrThrow()
        }
    }
}