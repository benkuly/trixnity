package net.folivo.trixnity.clientserverapi.server

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
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class MediaRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: MediaApiHandler

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

    @Test
    fun shouldGetMediaConfig() = testApplication {
        initCut()
        everySuspending { handlerMock.getMediaConfig(isAny()) }
            .returns(GetMediaConfig.Response(maxUploadSize = 50000000))
        val response = client.get("/_matrix/media/v3/config") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "m.upload.size":50000000
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getMediaConfig(isAny())
        }
    }

    @Test
    fun shouldCreateMedia() = testApplication {
        initCut()
        everySuspending { handlerMock.createMedia(isAny()) }
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
        verifyWithSuspend {
            handlerMock.createMedia(isAny())
        }
    }

    @Test
    fun shouldUploadMedia() = testApplication {
        initCut()
        everySuspending { handlerMock.uploadMedia(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.uploadMediaByContentUri(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.downloadMedia(isAny()) }
            .returns(
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    filename = "testFile.txt"
                )
            )
        val response =
            client.get("/_matrix/media/v3/download/matrix.org:443/ascERGshawAWawugaAcauga?allow_remote=false") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Text.Plain
            this.contentLength() shouldBe 4
            this.headers[HttpHeaders.ContentDisposition] shouldBe "testFile.txt"
            this.body<ByteReadChannel>().readUTF8Line() shouldBe "test"
        }
        verifyWithSuspend {
            handlerMock.downloadMedia(assert {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
                it.endpoint.allowRemote shouldBe false
            })
        }
    }

    @Test
    fun shouldDownloadThumbnail() = testApplication {
        initCut()
        everySuspending { handlerMock.downloadThumbnail(isAny()) }
            .returns(
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    filename = "testFile.txt"
                )
            )
        val response =
            client.get("/_matrix/media/v3/thumbnail/matrix.org:443/ascERGshawAWawugaAcauga?width=64&height=64&method=scale&allow_remote=false") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Text.Plain
            this.contentLength() shouldBe 4
            this.headers[HttpHeaders.ContentDisposition] shouldBe "testFile.txt"
            this.body<ByteReadChannel>().readUTF8Line() shouldBe "test"
        }
        verifyWithSuspend {
            handlerMock.downloadThumbnail(assert {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
                it.endpoint.width shouldBe 64
                it.endpoint.height shouldBe 64
                it.endpoint.method shouldBe ThumbnailResizingMethod.SCALE
                it.endpoint.allowRemote shouldBe false
            })
        }
    }

    @Test
    fun shouldGetUrlPreview() = testApplication {
        initCut()
        everySuspending { handlerMock.getUrlPreview(isAny()) }
            .returns(
                GetUrlPreview.Response(
                    size = 102400,
                    imageUrl = "mxc://example.com/ascERGshawAWawugaAcauga"
                )
            )
        val response = client.get("/_matrix/media/v3/preview_url?url=someUrl") { bearerAuth("token") }
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
        verifyWithSuspend {
            handlerMock.getUrlPreview(assert {
                it.endpoint.url shouldBe "someUrl"
            })
        }
    }
}
