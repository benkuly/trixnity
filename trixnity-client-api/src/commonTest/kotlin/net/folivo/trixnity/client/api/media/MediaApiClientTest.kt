package net.folivo.trixnity.client.api.media

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.api.MatrixApiClient
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MediaApiClientTest {
    @Test
    fun shouldGetConfig() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals("/_matrix/media/v3/config", request.url.fullPath)
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
                }
            })
        val result = matrixRestClient.media.getConfig().getOrThrow()
        assertEquals(GetConfigResponse(maxUploadSize = 50000000), result)
    }

    @Test
    fun shouldUploadFile() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
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
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        val result = matrixRestClient.media.upload(
            content = ByteReadChannel("test"),
            contentLength = 4,
            contentType = ContentType.Text.Plain,
            filename = "testFile.txt",
            progress = progress
        ).getOrThrow()
        result.contentUri shouldBe "mxc://example.com/AQwafuaFswefuhsfAFAgsw"
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldDownloadFile() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/media/v3/download/matrix.org/ascERGshawAWawugaAcauga?allow_remote=false",
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
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        val result = matrixRestClient.media.download(
            mxcUri = "mxc://matrix.org/ascERGshawAWawugaAcauga",
            allowRemote = false,
            progress = progress
        ).getOrThrow()
        result.content.toByteArray().decodeToString() shouldBe "test"
        result.contentLength shouldBe 4
        result.contentType shouldBe ContentType.Text.Plain
        progress.value shouldBe FileTransferProgress(4, 4)
    }

    @Test
    fun shouldDownloadThumbnail() = runTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/media/v3/thumbnail/matrix.org/ascERGshawAWawugaAcauga?width=64&height=64&method=scale&allow_remote=false",
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
                }
            })
        val progress = MutableStateFlow<FileTransferProgress?>(null)
        val result = matrixRestClient.media.downloadThumbnail(
            mxcUri = "mxc://matrix.org/ascERGshawAWawugaAcauga",
            width = 64u,
            height = 64u,
            method = ThumbnailResizingMethod.SCALE,
            allowRemote = false,
            progress = progress
        ).getOrThrow()
        result.content.toByteArray().decodeToString() shouldBe "test"
        result.contentLength shouldBe 4
        result.contentType shouldBe ContentType.Text.Plain
        progress.value shouldBe FileTransferProgress(4, 4)
    }
}