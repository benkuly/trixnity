package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.media.*
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.BeforeTest
import kotlin.test.Test

class MediaRoutesTest {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<MediaApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(ConvertMediaPlugin)
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                mediaApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetMediaConfig() = testApplication {
        initCut()
        everySuspend { handlerMock.getMediaConfig(any()) }
            .returns(GetMediaConfig.Response(maxUploadSize = 50000000))
        val response = client.get("/_matrix/client/v1/media/config") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "m.upload.size":50000000
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getMediaConfig(any())
        }
    }

    @Test
    fun shouldCreateMedia() = testApplication {
        initCut()
        everySuspend { handlerMock.createMedia(any()) }
            .returns(
                CreateMedia.Response(
                    contentUri = "mxc://example.com/AQwafuaFswefuhsfAFAgsw",
                    unusedExpiresAt = 1647257217083
                )
            )
        val response = client.post("/_matrix/media/v1/create") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                    "content_uri": "mxc://example.com/AQwafuaFswefuhsfAFAgsw",
                    "unused_expires_at": 1647257217083
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.createMedia(any())
        }
    }

    @Test
    fun shouldUploadMedia() = testApplication {
        initCut()
        everySuspend { handlerMock.uploadMedia(any()) }
            .returns(UploadMedia.Response("mxc://example.com/AQwafuaFswefuhsfAFAgsw"))
        val response = client.post("/_matrix/media/v3/upload?filename=testFile.txt") {
            bearerAuth("token")
            setBody(object : OutgoingContent.ReadChannelContent() {
                override fun readFrom() = ByteReadChannel("test")
                override val contentLength = 4L
                override val contentType = ContentType.Text.Plain
            })
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "content_uri":"mxc://example.com/AQwafuaFswefuhsfAFAgsw"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.uploadMedia(assert {
                it.endpoint.filename shouldBe "testFile.txt"
                it.requestBody.contentType shouldBe ContentType.Text.Plain
                it.requestBody.contentLength shouldBe 4
                runBlocking { it.requestBody.content.readUTF8Line() shouldBe "test" }
            })
        }
    }

    @Test
    fun shouldUploadMediaByContentUri() = testApplication {
        initCut()
        everySuspend { handlerMock.uploadMediaByContentUri(any()) }
            .returns(Unit)
        val response =
            client.post("/_matrix/media/v3/upload/example.com/AQwafuaFswefuhsfAFAgsw?filename=testFile.txt") {
                bearerAuth("token")
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override fun readFrom() = ByteReadChannel("test")
                    override val contentLength = 4L
                    override val contentType = ContentType.Text.Plain
                })
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {}
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.uploadMediaByContentUri(assert {
                it.endpoint.serverName shouldBe "example.com"
                it.endpoint.mediaId shouldBe "AQwafuaFswefuhsfAFAgsw"
                it.endpoint.filename shouldBe "testFile.txt"
                it.requestBody.contentType shouldBe ContentType.Text.Plain
                it.requestBody.contentLength shouldBe 4
                runBlocking { it.requestBody.content.readUTF8Line() shouldBe "test" }
            })
        }
    }

    @Test
    fun shouldDownloadMedia() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadMedia(any()) }
            .returns(
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    contentDisposition = ContentDisposition("attachment").withParameter("filename", "testFile.txt")
                )
            )
        val response =
            client.get("/_matrix/client/v1/media/download/matrix.org:443/ascERGshawAWawugaAcauga") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Text.Plain
            this.contentLength() shouldBe 4
            this.headers[HttpHeaders.ContentDisposition] shouldBe "attachment; filename=testFile.txt"
            this.body<ByteReadChannel>().readUTF8Line() shouldBe "test"
        }
        verifySuspend {
            handlerMock.downloadMedia(assert {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
            })
        }
    }

    @Test
    fun shouldDownloadThumbnail() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadThumbnail(any()) }
            .returns(
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    contentDisposition = ContentDisposition("attachment")
                        .withParameter("filename", "testFile.txt")
                )
            )
        val response =
            client.get("/_matrix/client/v1/media/thumbnail/matrix.org:443/ascERGshawAWawugaAcauga?width=64&height=64&method=scale") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Text.Plain
            this.contentLength() shouldBe 4
            this.headers[HttpHeaders.ContentDisposition] shouldBe "attachment; filename=testFile.txt"
            this.body<ByteReadChannel>().readUTF8Line() shouldBe "test"
        }
        verifySuspend {
            handlerMock.downloadThumbnail(assert {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
                it.endpoint.width shouldBe 64
                it.endpoint.height shouldBe 64
                it.endpoint.method shouldBe ThumbnailResizingMethod.SCALE
            })
        }
    }

    @Test
    fun shouldGetUrlPreview() = testApplication {
        initCut()
        everySuspend { handlerMock.getUrlPreview(any()) }
            .returns(
                GetUrlPreview.Response(
                    size = 102400,
                    imageUrl = "mxc://example.com/ascERGshawAWawugaAcauga"
                )
            )
        val response = client.get("/_matrix/client/v1/media/preview_url?url=someUrl") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "matrix:image:size": 102400,
                  "og:image": "mxc://example.com/ascERGshawAWawugaAcauga"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getUrlPreview(assert {
                it.endpoint.url shouldBe "someUrl"
            })
        }
    }
}
