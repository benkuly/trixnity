package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.media.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<MediaApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(ConvertMediaPlugin)
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    mediaApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @AfterTest
    fun afterTest() {
        verify(handlerMock).hasNoUnmetExpectations()
        verify(handlerMock).hasNoUnverifiedExpectations()
    }

    @Test
    fun shouldGetMediaConfig() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getMediaConfig)
            .whenInvokedWith(any())
            .then {
                GetMediaConfig.Response(maxUploadSize = 50000000)
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getMediaConfig)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun shouldUploadMedia() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::uploadMedia)
            .whenInvokedWith(any())
            .then {
                UploadMedia.Response("mxc://example.com/AQwafuaFswefuhsfAFAgsw")
            }
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
        verify(handlerMock).suspendFunction(handlerMock::uploadMedia)
            .with(matching {
                it.endpoint.filename shouldBe "testFile.txt"
                it.requestBody.contentType shouldBe ContentType.Text.Plain
                it.requestBody.contentLength shouldBe 4
                runBlocking { it.requestBody.content.readUTF8Line() shouldBe "test" }

                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDownloadMedia() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::downloadMedia)
            .whenInvokedWith(any())
            .then {
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    filename = "testFile.txt"
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::downloadMedia)
            .with(matching {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
                it.endpoint.allowRemote shouldBe false
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDownloadThumbnail() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::downloadThumbnail)
            .whenInvokedWith(any())
            .then {
                Media(
                    content = ByteReadChannel("test"),
                    contentLength = 4L,
                    contentType = ContentType.Text.Plain,
                    filename = "testFile.txt"
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::downloadThumbnail)
            .with(matching {
                it.endpoint.serverName shouldBe "matrix.org:443"
                it.endpoint.mediaId shouldBe "ascERGshawAWawugaAcauga"
                it.endpoint.width shouldBe 64
                it.endpoint.height shouldBe 64
                it.endpoint.method shouldBe ThumbnailResizingMethod.SCALE
                it.endpoint.allowRemote shouldBe false
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetUrlPreview() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getUrlPreview)
            .whenInvokedWith(any())
            .then {
                GetUrlPreview.Response(
                    size = 102400,
                    imageUrl = "mxc://example.com/ascERGshawAWawugaAcauga"
                )
            }
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
        verify(handlerMock).suspendFunction(handlerMock::getUrlPreview)
            .with(matching {
                it.endpoint.url shouldBe "someUrl"
                true
            })
            .wasInvoked()
    }
}
