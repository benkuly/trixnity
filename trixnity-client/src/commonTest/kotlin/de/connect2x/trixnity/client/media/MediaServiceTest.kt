package de.connect2x.trixnity.client.media

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryMediaCacheMapping
import de.connect2x.trixnity.client.getInMemoryServerDataStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.store.MediaCacheMapping
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.DownloadLimitExceededException
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.utils.decodeUnpaddedBase64Bytes
import de.connect2x.trixnity.utils.toByteArrayFlow
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.OctetStream
import io.ktor.http.ContentType.Text.Plain
import io.ktor.utils.io.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MediaServiceTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val mediaCacheMappingStore = getInMemoryMediaCacheMapping()
    private val serverDataStore = getInMemoryServerDataStore()
    private lateinit var coroutineScope: CoroutineScope

    private lateinit var mediaStore: InMemoryMediaStore

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private lateinit var cut: MediaService

    private val mxcUri = "mxc://example.com/abc"
    private val cacheUri = "upload://some-string"

    @BeforeTest
    fun beforeTest() {
        val config = MatrixClientConfiguration()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        mediaStore = InMemoryMediaStore(coroutineScope, config, Clock.System).apply { scheduleSetup { deleteAll() } }
        cut =
            MediaServiceImpl(
                api = api,
                mediaStore = mediaStore,
                serverDataStore = serverDataStore,
                mediaCacheMappingStore = mediaCacheMappingStore,
                tm = tm,
            )
    }

    @AfterTest
    fun afterTest() {
        coroutineScope.cancel()
    }

    @Test
    fun `getMedia » is mxc uri » prefer cache`() = runTest {
        mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(mxcUri, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"
    }

    @Test
    fun `getMedia » is mxc uri » download and cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }
        cut.getMedia(mxcUri, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"

        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `getMedia » is cache uri » prefer cache`() = runTest {
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(cacheUri, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"
    }

    @Test
    fun `getMedia » is cache uri » prefer cache but use mxcUri when already uploaded`() = runTest {
        tm.writeTransaction {
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) { MediaCacheMapping(cacheUri, mxcUri, 4) }
        }
        mediaStore.addMedia(mxcUri, "test".encodeToByteArray().toByteArrayFlow())
        cut.getMedia(cacheUri, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"
    }

    @Test
    fun `getMedia » file size too large » stop download`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(ByteReadChannel(ByteArray(1024)), HttpStatusCode.OK)
            }
        }
        val fileSizeLimit = 100L

        val exception = shouldThrow<DownloadLimitExceededException> { cut.getMedia(mxcUri, fileSizeLimit).getOrThrow() }

        exception.message shouldContain "File could not be downloaded because it would exceed the limit"
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `progress » getMedia » tracks download progress correctly`() = runTest {
        val data = ByteArray(5_000_000)
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(ByteReadChannel(data), HttpStatusCode.OK)
            }
        }
        val progress = FileTransferProgress(0, null)
        val progressFlow = MutableStateFlow<FileTransferProgress?>(progress)
        val emissions = mutableListOf<FileTransferProgress>()
        backgroundScope.launch { progressFlow.filterNotNull().collect { emissions.add(it) } }

        cut.getMedia(mxcUri, null, data.size.toLong(), progressFlow).getOrThrow().toByteArray() shouldBe data

        emissions shouldNotHaveSize 0
        emissions shouldBe emissions.sortedBy { it.transferred }
        emissions.last().transferred shouldBe data.size
        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe data
    }

    @Test
    fun `getMedia » insufficient space » stop download`() = runTest {
        mediaStore.availableSpace = 1000L
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }

        val expectedSize = 2000L
        val exception =
            shouldThrow<InsufficientSpaceException> {
                cut.getMedia(mxcUri, maxSize = null, expectedSize = expectedSize).getOrThrow()
            }

        exception.message shouldContain "insufficient for file"
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    private val rawFile = "lQ/twg".decodeUnpaddedBase64Bytes()
    private val encryptedFile =
        EncryptedFile(
            url = mxcUri,
            key = EncryptedFile.JWK(key = "BQ67pT94oS2ykjYwC63Xx9KoGNKrfRKJ3DyTaoEghWU"),
            initialisationVector = "xVA1MF7mXZ8AAAAAAAAAAA",
            hashes = mapOf("sha256" to "Hk9NwPYLemjX/b6MMxpLKYn632NkYSFaBEoEvj4Fzo4"),
        )

    @Test
    fun `getEncryptedMedia » prefer cache and decrypt`() = runTest {
        mediaStore.addMedia(mxcUri, rawFile.toByteArrayFlow())
        cut.getEncryptedMedia(encryptedFile, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe
            "test"
    }

    @Test
    fun `getEncryptedMedia » download cache and decrypt`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        cut.getEncryptedMedia(encryptedFile, maxSize = null).getOrThrow().toByteArray()?.decodeToString() shouldBe
            "test"
        mediaStore.media.value[mxcUri] shouldBe listOf(rawFile)
    }

    @Test
    fun `getEncryptedMedia » download not cache and decrypt`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        cut.getEncryptedMedia(encryptedFile, maxSize = null, saveToCache = false)
            .getOrThrow()
            .toByteArray()
            ?.decodeToString() shouldBe "test"
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
            cut.getEncryptedMedia(encryptedFileWithWrongHash, maxSize = null)
                .getOrThrow()
                .toByteArray()
                ?.decodeToString()
        }
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `getEncryptedMedia » file size too large » stop download`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        val fileSizeLimit = 1L

        val exception =
            shouldThrow<DownloadLimitExceededException> {
                cut.getEncryptedMedia(encryptedFile, fileSizeLimit)
                    .getOrThrow()
                    .toByteArray()
                    ?.decodeToString() shouldBe "test"
            }

        exception.message shouldContain "File could not be downloaded because it would exceed the limit"
        mediaStore.getMedia(mxcUri) shouldBe null
    }

    @Test
    fun `progress » getEncryptedMedia » tracks download progress correctly`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/download/example.com/abc"
                respond(rawFile, HttpStatusCode.OK)
            }
        }
        val progress = FileTransferProgress(0, null)
        val progressFlow = MutableStateFlow<FileTransferProgress?>(progress)
        val emissions = mutableListOf<FileTransferProgress>()
        backgroundScope.launch { progressFlow.filterNotNull().collect { emissions.add(it) } }

        cut.getEncryptedMedia(encryptedFile, maxSize = 1_000_000L, null, progressFlow)
            .getOrThrow()
            .toByteArray()
            ?.decodeToString() shouldBe "test"

        emissions shouldNotHaveSize 0
        emissions shouldBe emissions.sortedBy { it.transferred }
        emissions.last().transferred shouldBe 4
        mediaStore.media.value[mxcUri] shouldBe listOf(rawFile)
    }

    @Test
    fun `getThumbnail » prefer cache`() = runTest {
        mediaStore.addMedia("$mxcUri/32x32/crop", "test".encodeToByteArray().toByteArrayFlow())
        cut.getThumbnail(mxcUri, 32, 32, null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"
    }

    @Test
    fun `getThumbnail » download and cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/thumbnail/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }
        cut.getThumbnail(mxcUri, 32, 32, null).getOrThrow().toByteArray()?.decodeToString() shouldBe "test"
        mediaStore.getMedia("$mxcUri/32x32/crop")?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `getThumbnail » file size too large » stop download`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/thumbnail/example.com/abc"
                respond(ByteReadChannel(ByteArray(1024)), HttpStatusCode.OK)
            }
        }
        val fileSizeLimit = 100L

        val exception =
            shouldThrow<DownloadLimitExceededException> {
                cut.getThumbnail(mxcUri, 32, 32, maxSize = fileSizeLimit).getOrThrow()
            }

        exception.message shouldContain "File could not be downloaded because it would exceed the limit"
        mediaStore.getMedia("$mxcUri/32x32/crop") shouldBe null
    }

    @Test
    fun `progress » getThumbnail » tracks download progress correctly`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/client/v1/media/thumbnail/example.com/abc"
                respond(ByteReadChannel("test"), HttpStatusCode.OK)
            }
        }
        val progress = FileTransferProgress(0, null)
        val progressFlow = MutableStateFlow<FileTransferProgress?>(progress)
        val emissions = mutableListOf<FileTransferProgress>()
        backgroundScope.launch { progressFlow.filterNotNull().collect { emissions.add(it) } }

        cut.getThumbnail(mxcUri, 32, 32, null, progress = progressFlow)
            .getOrThrow()
            .toByteArray()
            ?.decodeToString() shouldBe "test"

        emissions shouldNotHaveSize 0
        emissions shouldBe emissions.sortedBy { it.transferred }
        emissions.last().transferred shouldBe 4
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
        mediaCacheMappingStore.getMediaCacheMapping(result.url) shouldBe
            MediaCacheMapping(result.url, null, 4, OctetStream.toString())
    }

    @Test
    fun `uploadMedia » upload and add to cache`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                respond(
                    """{"content_uri":"$mxcUri"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())),
                )
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        tm.writeTransaction {
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, 4, Plain.toString())
            }
        }

        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

        mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe
            MediaCacheMapping(cacheUri, mxcUri, 4, Plain.toString())
        mediaStore.getMedia(cacheUri) shouldBe null
        mediaStore.getMedia(mxcUri)?.toByteArray() shouldBe "test".encodeToByteArray()
    }

    @Test
    fun `uploadMedia » upload and remove from cache after that`() = runTest {
        apiConfig.endpoints {
            addHandler {
                it.url.encodedPath shouldBe "/_matrix/media/v3/upload"
                respond(
                    """{"content_uri":"$mxcUri"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())),
                )
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        tm.writeTransaction {
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, 4, Plain.toString())
            }
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
                    """{"content_uri":"$mxcUri"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())),
                )
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())
        tm.writeTransaction {
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, 4, Plain.toString())
            }
        }

        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri
        cut.uploadMedia(cacheUri).getOrThrow() shouldBe mxcUri

        calledCount shouldBe 1
    }

    @OptIn(MSC4143::class)
    @Test
    fun `uploadMedia » contain exception when file too large`() = runTest {
        val oldServerData = serverDataStore.getServerData()
        tm.writeTransaction {
            serverDataStore.setServerData(
                oldServerData.copy(mediaConfig = oldServerData.mediaConfig.copy(maxUploadSize = 4))
            )
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, 5, Plain.toString())
            }
        }
        shouldThrow<MediaTooLargeException> { cut.uploadMedia(cacheUri).getOrThrow() }
    }

    @Test
    fun `removeCachedMedia » remove media from store`() = runTest {
        tm.writeTransaction {
            mediaCacheMappingStore.updateMediaCacheMapping(cacheUri) {
                MediaCacheMapping(cacheUri, null, 5, Plain.toString())
            }
        }
        mediaStore.addMedia(cacheUri, "test".encodeToByteArray().toByteArrayFlow())

        cut.removeCachedMedia(cacheUri)

        mediaStore.getMedia(cacheUri) shouldBe null
        mediaCacheMappingStore.getMediaCacheMapping(cacheUri) shouldBe null
    }
}
