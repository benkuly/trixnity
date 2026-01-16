package de.connect2x.trixnity.clientserverapi.server

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.clientserverapi.model.authentication.IdentifierType
import de.connect2x.trixnity.clientserverapi.model.uia.*
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class MatrixUIAEndpointRouteTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = EventContentSerializerMappings.default

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
                matrixUIAEndpoint<PostPath, PostPath.Request, PostPath.Response>(json, mapping) {
                    endpoint.pathParam shouldBe "unicorn"
                    endpoint.requestParam shouldBe "2"
                    requestBody.request.includeDino shouldBe true
                    requestBody.authentication shouldBe null
                    ResponseWithUIA.Step(
                        UIAState(
                            flows = setOf(UIAState.FlowInformation(listOf(AuthenticationType.Password))),
                            session = "session",
                            parameter = mapOf(
                                AuthenticationType.Unknown("example.type.baz") to UIAState.Parameter.Unknown(
                                    buildJsonObject {
                                        put("example_key", JsonPrimitive("foobar"))
                                    }
                                ),
                                AuthenticationType.TermsOfService to UIAState.Parameter.TermsOfService(
                                    mapOf(
                                        "terms_of_service" to UIAState.Parameter.TermsOfService.PolicyDefinition(
                                            "1.2", mapOf(
                                                "en" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                                    "Terms of Service",
                                                    "https://example.org/somewhere/terms-1.2-en.html"
                                                ),
                                                "fr" to UIAState.Parameter.TermsOfService.PolicyDefinition.PolicyTranslation(
                                                    "Conditions d'utilisation",
                                                    "https://example.org/somewhere/terms-1.2-fr.html"
                                                ),
                                            )
                                        )
                                    )
                                )
                            ),
                        )
                    )
                }
            }
        }
        val response = client.post("/path/unicorn?requestParam=2") {
            contentType(ContentType.Application.Json)
            setBody("""{"includeDino":true}""")
        }
        response.body<String>() shouldBe """
            {
              "completed": [],
              "flows": [
                {
                  "stages": [
                    "m.login.password"
                  ]
                }
              ],
              "params": {
                "example.type.baz": {
                  "example_key": "foobar"
                },
                "m.login.terms": {
                  "policies": {
                    "terms_of_service": {
                      "version": "1.2",
                      "en": {
                        "name": "Terms of Service",
                        "url": "https://example.org/somewhere/terms-1.2-en.html"
                      },
                      "fr": {
                        "name": "Conditions d'utilisation",
                        "url": "https://example.org/somewhere/terms-1.2-fr.html"
                      }
                    }
                  }
                }
              },
              "session": "session"
            }
        """.trimToFlatJson()
        response.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
        response.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun shouldHandleRequestWithErrorResponse() = testApplication {
        application {
            matrixApiServer(json) {
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