package net.folivo.trixnity.api.server

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.Test

class MatrixEndpointRouteTest {
    private val json = createMatrixJson()

    @Serializable
    @Resource("/path/{pathParam}")
    data class PostPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixJsonEndpoint<PostPath.Request, PostPath.Response>() {
        @Transient
        override val method = HttpMethod.Post

        @Serializable
        data class Request(
            val includeDino: Boolean
        )

        @Serializable
        data class Response(
            val status: String
        )
    }

    @Test
    fun shouldHandleRequest() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<PostPath, PostPath.Request, PostPath.Response>(json) {
                        endpoint.pathParam shouldBe "unicorn"
                        endpoint.requestParam shouldBe "2"
                        requestBody.includeDino shouldBe true
                        PostPath.Response("dino")
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        response.body<String>() shouldBe """{"status":"dino"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldHandleRequestWithCustomSerializers() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<PostPath, PostPath.Request, PostPath.Response>(json,
                        requestSerializer = object : KSerializer<PostPath.Request> {
                            override val descriptor = buildClassSerialDescriptor("customRequestSerializer")

                            override fun deserialize(decoder: Decoder): PostPath.Request {
                                require(decoder is JsonDecoder)
                                decoder.decodeJsonElement()
                                return PostPath.Request(false)
                            }

                            override fun serialize(encoder: Encoder, value: PostPath.Request) {
                                throw NotImplementedError()
                            }

                        },
                        responseSerializer = object : KSerializer<PostPath.Response> {
                            override val descriptor = buildClassSerialDescriptor("customResponseSerializer")

                            override fun deserialize(decoder: Decoder): PostPath.Response {
                                throw NotImplementedError()
                            }

                            override fun serialize(encoder: Encoder, value: PostPath.Response) {
                                require(encoder is JsonEncoder)
                                encoder.encodeJsonElement(JsonObject(mapOf("custom" to JsonPrimitive(true))))
                            }

                        }) {
                        requestBody.includeDino shouldBe false
                        PostPath.Response("dino")
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        response.body<String>() shouldBe """{"custom":true}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.OK
    }

    @Serializable
    @Resource("/unit")
    object UnitPath : MatrixJsonEndpoint<Unit, Unit>() {
        @Transient
        override val method = HttpMethod.Get
    }

    @Test
    fun shouldHandleUnitRequestAndResponse() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<UnitPath, Unit, Unit>(json) {
                        requestBody shouldBe Unit
                    }
                }
            }
        }
        val response = client.get("/unit") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        response.body<String>() shouldBe """{}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response.status shouldBe HttpStatusCode.OK
    }

}