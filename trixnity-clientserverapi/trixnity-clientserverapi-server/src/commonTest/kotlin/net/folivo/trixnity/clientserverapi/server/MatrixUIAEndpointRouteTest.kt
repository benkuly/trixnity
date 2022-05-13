package net.folivo.trixnity.clientserverapi.server

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.authentication.IdentifierType
import net.folivo.trixnity.clientserverapi.model.uia.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test

class MatrixUIAEndpointRouteTest {
    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Serializable
    @Resource("/path/{pathParam}")
    @HttpMethod(POST)
    data class PostPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixUIAEndpoint<PostPath.Request, PostPath.Response> {
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
    @HttpMethod(GET)
    data class GetPath(
        @SerialName("pathParam") val pathParam: String,
        @SerialName("requestParam") val requestParam: String,
    ) : MatrixUIAEndpoint<Unit, GetPath.Response> {
        @Serializable
        data class Response(
            val status: String
        )
    }

    @Test
    fun shouldHandleRequestWithSuccessResponse() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixUIAEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, mapping) {
                        endpoint.pathParam shouldBe "unicorn"
                        endpoint.requestParam shouldBe "2"
                        requestBody.request.includeDino shouldBe true
                        requestBody.authentication shouldBe AuthenticationRequestWithSession(
                            AuthenticationRequest.Password(
                                IdentifierType.User("user"),
                                "password"
                            ), "session"
                        )
                        ResponseWithUIA.Success(
                            PostPath.Response("dino")
                        )
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody(
                """{
                    "includeDino":true,
                    "auth":{
                        "type": "m.login.password",
                        "identifier": {
                            "type": "m.id.user",
                            "user": "user"
                        },
                        "password": "password",
                        "session": "session"
                    }
                }""".trimIndent()
            )
        }
        response.body<String>() shouldBe """{"status":"dino"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
        response.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldHandleRequestWithStepResponse() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixUIAEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, mapping) {
                        endpoint.pathParam shouldBe "unicorn"
                        endpoint.requestParam shouldBe "2"
                        requestBody.request.includeDino shouldBe true
                        requestBody.authentication shouldBe null
                        ResponseWithUIA.Step(
                            UIAState(
                                flows = setOf(UIAState.FlowInformation(listOf(AuthenticationType.Password))),
                                session = "session"
                            )
                        )
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        response.body<String>() shouldBe """{"completed":[],"flows":[{"stages":["m.login.password"]}],"session":"session"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun shouldHandleRequestWithErrorResponse() = testApplication {
        application {
            matrixApiServer(json) {
                routing {
                    matrixUIAEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, mapping) {
                        endpoint.pathParam shouldBe "unicorn"
                        endpoint.requestParam shouldBe "2"
                        requestBody.request.includeDino shouldBe true
                        requestBody.authentication shouldBe null
                        ResponseWithUIA.Error(
                            UIAState(
                                flows = setOf(UIAState.FlowInformation(listOf(AuthenticationType.Password))),
                                session = "session"
                            ),
                            ErrorResponse.Unauthorized("password wrong")
                        )
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        response.body<String>() shouldBe """{"completed":[],"flows":[{"stages":["m.login.password"]}],"session":"session","errcode":"M_UNAUTHORIZED","error":"password wrong"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun shouldIgnoreWrongHttpMethod() = testApplication {
        var getHasBeenCalled = false
        application {
            matrixApiServer(json) {
                routing {
                    matrixUIAEndpoint<GetPath, Unit, GetPath.Response>(json, mapping) {
                        getHasBeenCalled = true
                        ResponseWithUIA.Success(
                            GetPath.Response("anti-dino")
                        )
                    }
                    matrixUIAEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, mapping) {
                        ResponseWithUIA.Success(
                            PostPath.Response("dino")
                        )
                    }
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        getHasBeenCalled shouldBe false
        response.body<String>() shouldBe """{"status":"dino"}"""
        response.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
        response.status shouldBe HttpStatusCode.OK
    }
}