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
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.test.Test

class MatrixEndpointRouteTest {
    private val json = createMatrixJson()
    private val contentMappings = createEventContentSerializerMappings()

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(net.folivo.trixnity.core.HttpMethodType.POST)
    data class PostPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPath.Request, PostPath.Response> {
        @Serializable
        data class Request(
            val includeDino: Boolean
        )

        @Serializable
        data class Response(
            val status: String
        )
    }

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(net.folivo.trixnity.core.HttpMethodType.GET)
    data class GetPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<Unit, GetPath.Response> {
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
                    matrixEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, contentMappings) {
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
    fun shouldIgnoreWrongHttpMethod() = testApplication {
        var getHasBeenCalled = false
        var postHasBeenCalled = false
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<GetPath, GetPath.Response>(json, contentMappings) {
                        getHasBeenCalled = true
                        GetPath.Response("anti-dino")
                    }
                    matrixEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, contentMappings) {
                        postHasBeenCalled = true
                        PostPath.Response("dino")
                    }
                }
            }
        }
        val response1 = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        getHasBeenCalled shouldBe false
        postHasBeenCalled shouldBe true
        response1.body<String>() shouldBe """{"status":"dino"}"""
        response1.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response1.status shouldBe HttpStatusCode.OK

        getHasBeenCalled = false
        postHasBeenCalled = false

        val response2 = client.get("/path/unicorn?requestParam=2")
        getHasBeenCalled shouldBe true
        postHasBeenCalled shouldBe false
        response2.body<String>() shouldBe """{"status":"anti-dino"}"""
        response2.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
        response2.status shouldBe HttpStatusCode.OK
    }

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(net.folivo.trixnity.core.HttpMethodType.POST)
    data class PostPathWithCustomSerializer(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixEndpoint<PostPathWithCustomSerializer.Request, PostPathWithCustomSerializer.Response> {
        @Serializable
        data class Request(
            val includeDino: Boolean
        )

        @Serializable
        data class Response(
            val status: String
        )

        override fun requestSerializerBuilder(
            mappings: EventContentSerializerMappings,
            json: Json
        ): KSerializer<Request> {
            return object : KSerializer<Request> {
                override val descriptor = buildClassSerialDescriptor("customRequestSerializer")

                override fun deserialize(decoder: Decoder): Request {
                    require(decoder is JsonDecoder)
                    decoder.decodeJsonElement()
                    return Request(false)
                }

                override fun serialize(encoder: Encoder, value: Request) {
                    throw NotImplementedError()
                }

            }
        }

        override fun responseSerializerBuilder(
            mappings: EventContentSerializerMappings,
            json: Json
        ): KSerializer<Response> {
            return object : KSerializer<Response> {
                override val descriptor = buildClassSerialDescriptor("customResponseSerializer")

                override fun deserialize(decoder: Decoder): Response {
                    throw NotImplementedError()
                }

                override fun serialize(encoder: Encoder, value: Response) {
                    require(encoder is JsonEncoder)
                    encoder.encodeJsonElement(JsonObject(mapOf("custom" to JsonPrimitive(true))))
                }

            }
        }
    }

    @Test
    fun shouldHandleRequestWithCustomSerializers() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<PostPathWithCustomSerializer, PostPathWithCustomSerializer.Request, PostPathWithCustomSerializer.Response>(
                        json,
                        contentMappings
                    ) {
                        requestBody.includeDino shouldBe false
                        PostPathWithCustomSerializer.Response("dino")
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
    @HttpMethod(net.folivo.trixnity.core.HttpMethodType.GET)
    object UnitPath : MatrixEndpoint<Unit, Unit>

    @Test
    fun shouldHandleUnitRequestAndResponse() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixEndpoint<UnitPath, Unit, Unit>(json, contentMappings) {
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