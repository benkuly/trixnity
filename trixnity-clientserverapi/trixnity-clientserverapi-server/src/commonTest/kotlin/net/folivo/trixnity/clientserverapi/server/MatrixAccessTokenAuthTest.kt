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
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import kotlin.test.Test

class MatrixAccessTokenAuthTest {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()
    private val principal = MatrixClientPrincipal(UserId("user", "server"), "deviceId")

    private fun ApplicationTestBuilder.testEndpoint(
        authenticationFunction: AccessTokenAuthenticationFunction = AccessTokenAuthenticationFunction {
            it.accessToken shouldBe "accessToken"
            AccessTokenAuthenticationFunctionResult(principal, null)
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
        result.body<String>() shouldBe """{"errcode":"M_MISSING_TOKEN","error":"missing token"}"""
    }

    @Test
    fun shouldRespondWitUnknownTokenWhenTokenIsWrong() = testApplication {
        testEndpoint {
            AccessTokenAuthenticationFunctionResult(null, AuthenticationFailedCause.InvalidCredentials)
        }
        val result = client.get("/test?access_token=wrong")
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNKNOWN_TOKEN","error":"invalid token","soft_logout":false}"""
    }

    @Test
    fun shouldRespondWitUnknownTokenWhenPrincipalIsNull() = testApplication {
        testEndpoint {
            AccessTokenAuthenticationFunctionResult(null, null)
        }
        val result = client.get("/test?access_token=wrong")
        result.status shouldBe HttpStatusCode.Unauthorized
        result.body<String>() shouldBe """{"errcode":"M_UNKNOWN_TOKEN","error":"invalid token","soft_logout":false}"""
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
                this.authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(principal, null)
                }
            }
            routing {
                authenticate {
                    get("/test") {
                        call.matrixClientPrincipal() shouldBe principal
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
    @Auth(AuthRequired.NO)
    object GetResourceWithoutAuth : MatrixEndpoint<Unit, Unit>

    @Serializable
    @Resource("/get")
    @HttpMethod(GET)
    @Auth(AuthRequired.OPTIONAL)
    object GetResourceWithOptionalAuth : MatrixEndpoint<Unit, Unit>

    @Test
    fun shouldAuthenticateWhenResourceWantsIt() = testApplication {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            install(Resources)
            installMatrixAccessTokenAuth {
                this.authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            routing {
                authenticate {
                    matrixEndpoint<GetResourceWithAuth, Unit, Unit>(json, mapping) {
                        call.respond(HttpStatusCode.OK)
                    }
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
                this.authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
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

    @Test
    fun shouldAuthenticateWhenResourceOptionallyAllowsIt() = testApplication {
        var observedPrincipal: MatrixClientPrincipal? = null
        application {
            install(ContentNegotiation) {
                json(json)
            }
            install(Resources)
            installMatrixAccessTokenAuth {
                this.authenticationFunction = AccessTokenAuthenticationFunction {
                    it.accessToken shouldBe "accessToken"
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            routing {
                authenticate {
                    matrixEndpoint<GetResourceWithOptionalAuth, Unit, Unit>(json, mapping) {
                        observedPrincipal = call.principal<MatrixClientPrincipal>()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        client.get("/get").status shouldBe HttpStatusCode.OK
        observedPrincipal shouldBe null

        client.get("/get") {
            header(HttpHeaders.Authorization, "Bearer accessToken")
        }.status shouldBe HttpStatusCode.OK
        observedPrincipal shouldBe principal
    }
}