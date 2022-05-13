package net.folivo.trixnity.serverserverapi.server

import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.serverserverapi.model.RequestAuthenticationBody
import kotlin.test.Test

class MatrixSignatureAuthTest {
    private val json = createMatrixDataUnitJson({ "3" })
    private val mapping = createEventContentSerializerMappings()

    private fun ApplicationTestBuilder.testEndpoint(
        authenticationFunction: SignatureAuthenticationFunction = {
            it shouldBe Signed(
                RequestAuthenticationBody(
                    method = "POST",
                    uri = "/test",
                    origin = "other.hs.host",
                    destination = "own.hs.host",
                    content = "{}"
                ),
                mapOf("other.hs.host" to keysOf(Key.Ed25519Key("key1", "sig")))
            )
            SignatureAuthenticationFunctionResult(UserIdPrincipal("dino"), null)
        }
    ) {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            installMatrixSignatureAuth(hostname = "own.hs.host") {
                this.authenticationFunction = authenticationFunction
            }
            routing {
                authenticate {
                    post("/test") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }

    @Test
    fun shouldUseSignatureFromHeader() = testApplication {
        testEndpoint()
        client.post("/test") {
            setBody("{}")
            header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")
        }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldRespondWithMissingSignatureWhenSignatureIsMissing() = testApplication {
        testEndpoint()
        val result = client.post("/test") {
            setBody("{}")
        }
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNAUTHORIZED","error":"missing signature"}"""
    }

    @Test
    fun shouldRespondWithMissingSignatureWhenSignatureIsWrong() = testApplication {
        testEndpoint {
            SignatureAuthenticationFunctionResult(null, AuthenticationFailedCause.InvalidCredentials)
        }
        val result = client.post("/test") {
            setBody("{}")
            header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")
        }
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNAUTHORIZED","error":"wrong signature"}"""
    }

    @Test
    fun shouldRespondWithMissingSignatureWhenPrincipalIsNull() = testApplication {
        testEndpoint {
            SignatureAuthenticationFunctionResult(null, null)
        }
        val result = client.post("/test") {
            setBody("{}")
            header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")
        }
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNAUTHORIZED","error":"wrong signature"}"""
    }

    @Test
    fun shouldAllowRetrievePrincipal() = testApplication {
        application {
            installMatrixSignatureAuth(hostname = "own.hs.host") {
                this.authenticationFunction = {
                    SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null)
                }
            }
            routing {
                install(ContentNegotiation) {
                    json(json)
                }
                authenticate {
                    get("/test") {
                        call.principal<UserIdPrincipal>() shouldBe UserIdPrincipal("user")
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        client.get("/test?access_token=right")
    }

    @Serializable
    @Resource("/get")
    @HttpMethod(GET)
    object GetResourceWithAuth : MatrixEndpoint<Unit, Unit>

    @Serializable
    @Resource("/get")
    @HttpMethod(GET)
    @WithoutAuth
    object GetResourceWithoutAuth : MatrixEndpoint<Unit, Unit>

    @Test
    fun shouldAuthenticateWhenResourceWantsIt() = testApplication {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            install(Resources)
            installMatrixSignatureAuth(hostname = "own.hs.host") {
                this.authenticationFunction = {
                    SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null)
                }
            }
            routing {
                authenticate {
                    matrixEndpoint<GetResourceWithAuth, Unit, Unit>(json, mapping) { call.respond(HttpStatusCode.OK) }
                }
            }
        }
        client.get("/get").status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun shouldNotAuthenticateWhenResourceDoesNotWantIt() = testApplication {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            install(Resources)
            installMatrixSignatureAuth(hostname = "own.hs.host") {
                this.authenticationFunction = {
                    SignatureAuthenticationFunctionResult(null, null)
                }
            }
            routing {
                authenticate {
                    matrixEndpoint<GetResourceWithoutAuth, Unit, Unit>(json, mapping) {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        client.get("/get").status shouldBe HttpStatusCode.OK
    }
}