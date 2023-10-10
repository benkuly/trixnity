package net.folivo.trixnity.clientserverapi.server

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
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test

class MatrixAccessTokenAuthTest {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    private fun ApplicationTestBuilder.testEndpoint(
        authenticationFunction: AccessTokenAuthenticationFunction = {
            it.accessToken shouldBe "accessToken"
            AccessTokenAuthenticationFunctionResult(UserIdPrincipal("dino"), null)
        }
    ) {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            installMatrixAccessTokenAuth {
                this.authenticationFunction = authenticationFunction
            }
            routing {
                authenticate {
                    get("/test") {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
    }

    @Test
    fun shouldUseAccessTokenFromHeader() = testApplication {
        testEndpoint()
        client.get("/test") {
            header(HttpHeaders.Authorization, "Bearer accessToken")
        }.status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldUseAccessTokenFromQueryParameter() = testApplication {
        testEndpoint()
        client.get("/test?access_token=accessToken").status shouldBe HttpStatusCode.OK
    }

    @Test
    fun shouldRespondWithMissingTokenWhenTokenIsMissing() = testApplication {
        testEndpoint()
        val result = client.get("/test")
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_MISSING_TOKEN"}"""
    }

    @Test
    fun shouldRespondWitUnknownTokenWhenTokenIsWrong() = testApplication {
        testEndpoint {
            AccessTokenAuthenticationFunctionResult(null, AuthenticationFailedCause.InvalidCredentials)
        }
        val result = client.get("/test?access_token=wrong")
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNKNOWN_TOKEN","soft_logout":false}"""
    }

    @Test
    fun shouldRespondWitUnknownTokenWhenPrincipalIsNull() = testApplication {
        testEndpoint {
            AccessTokenAuthenticationFunctionResult(null, null)
        }
        val result = client.get("/test?access_token=wrong")
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNKNOWN_TOKEN","soft_logout":false}"""
    }

    @Test
    fun shouldRespondWitInternalServerErrorWhenAuthenticationFunctionHasError() = testApplication {
        testEndpoint {
            AccessTokenAuthenticationFunctionResult(null, AuthenticationFailedCause.Error("doppelwumms"))
        }
        val result = client.get("/test?access_token=wrong")
        result.status shouldBe HttpStatusCode.InternalServerError
        result.body<String>() shouldBe """{"errcode":"M_UNKNOWN","error":"doppelwumms"}"""
    }

    @Test
    fun shouldAllowRetrievePrincipal() = testApplication {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            installMatrixAccessTokenAuth {
                this.authenticationFunction = {
                    AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null)
                }
            }
            routing {
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
            installMatrixAccessTokenAuth {
                this.authenticationFunction = {
                    AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null)
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
            installMatrixAccessTokenAuth {
                this.authenticationFunction = {
                    AccessTokenAuthenticationFunctionResult(null, null)
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