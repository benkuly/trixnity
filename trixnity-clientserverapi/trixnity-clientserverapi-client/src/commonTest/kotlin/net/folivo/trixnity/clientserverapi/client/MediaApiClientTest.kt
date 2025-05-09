package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.clientserverapi.model.media.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaApiClientTest : TrixnityBaseTest() {
    @Test
    fun shouldGetConfig() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/media/config", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                                {
                                  "m.upload.size": 50000000
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.media.getConfig().getOrThrow()
        assertEquals(GetMediaConfig.Response(maxUploadSize = 50000000), result)
    }

    @Test
    fun shouldCreateMedia() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/media/v1/create", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    respond(
                        """
                                {
                                    "content_uri": "mxc://example.com/AQwafuaFswefuhsfAFAgsw",
                                    "unused_expires_at": 1647257217083
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.media.createMedia().getOrThrow()
        assertEquals(
            CreateMedia.Response(
                contentUri = "mxc://example.com/AQwafuaFswefuhsfAFAgsw",
                unusedExpiresAt = 1647257217083
            ), result
        )
    }

    @Test
    fun shouldUploadFile() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/media/v3/upload?filename=testFile.txt", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(ContentType.Text.Plain, request.body.contentType)
                    assertEquals(4, request.body.contentLength)
                    assertEquals("test", request.body.toByteArray().decodeToString())
                    respond(
                        """
                                {
                                  "content_uri": "mxc://example.com/AQwafuaFswefuhsfAFAgsw"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        val result = matrixRestClient.media.upload(
            Media(
                content = ByteReadChannel("test"),
                contentLength = 4,
                contentType = ContentType.Text.Plain,
                contentDisposition = ContentDisposition("attachment").withParameter("filename", "testFile.txt")
            ),
            progress = progress
        ).getOrThrow()
        result.contentUri shouldBe "mxc://example.com/AQwafuaFswefuhsfAFAgsw"
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldUploadJsonFile() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/media/v3/upload?filename=testFile.json", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    assertEquals(2, request.body.contentLength)
                    assertEquals("{}", request.body.toByteArray().decodeToString())
                    respond(
                        """
                                {
                                  "content_uri": "mxc://example.com/AQwafuaFswefuhsfAFAgsw"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        val result = matrixRestClient.media.upload(
            Media(
                content = ByteReadChannel("{}"),
                contentLength = 2,
                contentType = ContentType.Application.Json,
                contentDisposition = ContentDisposition("attachment").withParameter("filename", "testFile.json")
            ),
            progress = progress
        ).getOrThrow()
        result.contentUri shouldBe "mxc://example.com/AQwafuaFswefuhsfAFAgsw"
        progress.value shouldBe FileTransferProgress(2, 2)
    }

    @Test
    fun shouldUploadFileByContentUri() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/media/v3/upload/example.com/AQwafuaFswefuhsfAFAgsw?filename=testFile.txt",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(ContentType.Text.Plain, request.body.contentType)
                    assertEquals(4, request.body.contentLength)
                    assertEquals("test", request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        matrixRestClient.media.upload(
            serverName = "example.com",
            mediaId = "AQwafuaFswefuhsfAFAgsw",
            media = Media(
                content = ByteReadChannel("test"),
                contentLength = 4,
                contentType = ContentType.Text.Plain,
                contentDisposition = ContentDisposition("attachment").withParameter("filename", "testFile.txt")
            ),
            progress = progress
        ).getOrThrow()
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldDownloadFile() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/media/download/matrix.org:443/ascERGshawAWawugaAcauga",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        ByteReadChannel("test"),
                        HttpStatusCode.OK,
                        headersOf(
                            Pair(HttpHeaders.ContentType, listOf(ContentType.Text.Plain.toString())),
                            Pair(HttpHeaders.ContentLength, listOf("4"))
                        )
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        matrixRestClient.media.download(
            mxcUri = "mxc://matrix.org:443/ascERGshawAWawugaAcauga",
            progress = progress
        ) { result ->
            result.content.toByteArray().decodeToString() shouldBe "test"
            result.contentLength shouldBe 4
            result.contentType shouldBe ContentType.Text.Plain
        }.getOrThrow()
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldDownloadJsonFile() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/media/download/matrix.org:443/ascERGshawAWawugaAcauga",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        ByteReadChannel("{}"),
                        HttpStatusCode.OK,
                        headersOf(
                            Pair(HttpHeaders.ContentType, listOf(ContentType.Application.Json.toString())),
                            Pair(HttpHeaders.ContentLength, listOf("2"))
                        )
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        matrixRestClient.media.download(
            mxcUri = "mxc://matrix.org:443/ascERGshawAWawugaAcauga",
            progress = progress
        ) { result ->
            result.content.toByteArray().decodeToString() shouldBe "{}"
            result.contentLength shouldBe 2
            result.contentType shouldBe ContentType.Application.Json
        }.getOrThrow()
        progress.value shouldBe FileTransferProgress(2, 2)
    }

    @Test
    fun shouldDownloadThumbnail() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v1/media/thumbnail/matrix.org:443/ascERGshawAWawugaAcauga?width=64&height=64&method=scale",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        ByteReadChannel("test"),
                        HttpStatusCode.OK,
                        headersOf(
                            Pair(HttpHeaders.ContentType, listOf(ContentType.Text.Plain.toString())),
                            Pair(HttpHeaders.ContentLength, listOf("4"))
                        )
                    )
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        matrixRestClient.media.downloadThumbnail(
            mxcUri = "mxc://matrix.org:443/ascERGshawAWawugaAcauga",
            width = 64,
            height = 64,
            method = ThumbnailResizingMethod.SCALE,
            progress = progress
        ) { result ->
            result.content.toByteArray().decodeToString() shouldBe "test"
            result.contentLength shouldBe 4
            result.contentType shouldBe ContentType.Text.Plain
        }.getOrThrow()
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldGetUrlPreview() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v1/media/preview_url?url=someUrl", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                                {
                                  "matrix:image:size": 102400,
                                  "og:description": "This is a really cool blog post from matrix.org",
                                  "og:image": "mxc://example.com/ascERGshawAWawugaAcauga",
                                  "og:image:height": 48,
                                  "og:image:type": "image/png",
                                  "og:image:width": 48,
                                  "og:title": "Matrix Blog Post"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.media.getUrlPreview("someUrl").getOrThrow() shouldBe GetUrlPreview.Response(
            size = 102400,
            imageUrl = "mxc://example.com/ascERGshawAWawugaAcauga"
        )
    }
}