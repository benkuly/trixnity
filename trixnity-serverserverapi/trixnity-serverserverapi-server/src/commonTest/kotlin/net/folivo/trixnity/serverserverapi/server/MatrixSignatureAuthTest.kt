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
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import kotlin.test.Test

class MatrixSignatureAuthTest {
    private val json = createMatrixDataUnitJson({ "3" })
    private val mapping = createDefaultEventContentSerializerMappings()

    private fun ApplicationTestBuilder.testEndpoint(
        authenticationFunction: SignatureAuthenticationFunction = {
            it shouldBe SignedRequestAuthenticationBody(
                signed = """{"content":{},"destination":"own.hs.host","method":"POST","origin":"other.hs.host","uri":"/test"}""",
                signature = Key.Ed25519Key("key1", "sig"),
                origin = "other.hs.host",
            )
            SignatureAuthenticationFunctionResult(UserIdPrincipal("dino"), null)
        }
    ) {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            installMatrixSignatureAuth(hostname = "fallback.own.hs.host") {
                this.authenticationFunction = authenticationFunction
            }
            routing {
                authenticate {
                    post("/test") {
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/test") {
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
            contentType(ContentType.Application.Json)
            header(
                HttpHeaders.Authorization,
                """X-Matrix origin=other.hs.host,destination=own.hs.host,key="ed25519:key1",sig="sig""""
            )
        }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldAllowFallbackDestination() = testApplication {
        testEndpoint {
            it shouldBe SignedRequestAuthenticationBody(
                signed = """{"content":{},"destination":"fallback.own.hs.host","method":"POST","origin":"other.hs.host","uri":"/test"}""",
                signature = Key.Ed25519Key("key1", "sig"),
                origin = "other.hs.host",
            )
            SignatureAuthenticationFunctionResult(UserIdPrincipal("dino"), null)
        }
        client.post("/test") {
            setBody("{}")
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")
        }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldUseSignatureFromHeaderWithNoBody() = testApplication {
        testEndpoint {
            it shouldBe SignedRequestAuthenticationBody(
                signed = """{"destination":"own.hs.host","method":"GET","origin":"other.hs.host","uri":"/test"}""",
                signature = Key.Ed25519Key("key1", "sig"),
                origin = "other.hs.host",
            )
            SignatureAuthenticationFunctionResult(UserIdPrincipal("dino"), null)
        }
        client.get("/test") {
            header(
                HttpHeaders.Authorization,
                """X-Matrix origin=other.hs.host,destination=own.hs.host,key="ed25519:key1",sig="sig""""
            )
        }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldRespondWithMissingSignatureWhenSignatureIsMissing() = testApplication {
        testEndpoint()
        val result = client.post("/test") {
            contentType(ContentType.Application.Json)
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
            contentType(ContentType.Application.Json)
            setBody("{}")
            header(
                HttpHeaders.Authorization,
                """X-Matrix origin=other.hs.host,destination=own.hs.host,key="ed25519:key1",sig="sig""""
            )
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
            contentType(ContentType.Application.Json)
            setBody("{}")
            header(
                HttpHeaders.Authorization,
                """X-Matrix origin=other.hs.host,destination=own.hs.host,key="ed25519:key1",sig="sig""""
            )
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
                    matrixEndpoint<GetResourceWithAuth, Unit, Unit>(
                        json,
                        mapping
                    ) { call.respond(HttpStatusCode.OK) }
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