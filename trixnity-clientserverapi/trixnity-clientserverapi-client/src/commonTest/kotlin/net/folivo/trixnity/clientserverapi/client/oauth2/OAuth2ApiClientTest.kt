package net.folivo.trixnity.clientserverapi.client.oauth2

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.client.trimToFlatJson
import net.folivo.trixnity.clientserverapi.model.authentication.oauth2.*
import net.folivo.trixnity.core.MSC4191
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test

class OAuth2ApiClientTest {

    @OptIn(MSC4191::class)
    private val serverMetadata = ServerMetadata(
        issuer = Url("https://auth.matrix.host"),
        authorizationEndpoint = Url("https://auth.matrix.host/authorize"),
        registrationEndpoint = Url("https://auth.matrix.host/registration"),
        revocationEndpoint = Url("https://auth.matrix.host/revoke"),
        tokenEndpoint = Url("https://auth.matrix.host/token"),
        codeChallengeMethodsSupported = setOf(CodeChallengeMethod.S256),
        responseTypesSupported = setOf(ResponseType.Code),
        responseModesSupported = setOf(ResponseMode.Query),
        promptValuesSupported = setOf(PromptValue.Create),
        grantTypesSupported = setOf(GrantType.RefreshToken, GrantType.AuthorizationCode)
    )

    @Test
    @OptIn(MSC4191::class)
    fun shouldRegisterClient() = runTest {
        val clientMetadata = ClientMetadata(
            applicationType = ApplicationType.Web,
            clientUri = "https://client.example.com",
            redirectUris = setOf("https://localhost:8080/redirect"),
            grantTypes = setOf(GrantType.RefreshToken, GrantType.AuthorizationCode),
            responseTypes = setOf(ResponseType.Code),
            tokenEndpointAuthMethod = TokenEndpointAuthMethod.None,
            clientName = LocalizedField("Trixnity")
        )

        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/registration"
                    request.body.toByteArray().decodeToString() shouldBe """
                        {
                            "application_type": "web",
                            "client_name": {
                                "default": "Trixnity"
                            },
                            "client_uri": "https://client.example.com",
                            "grant_types": [
                                "refresh_token",
                                "authorization_code"
                            ],
                            "redirect_uris": [
                                "https://localhost:8080/redirect"
                            ],
                            "response_types": [
                                "code"
                            ],
                            "token_endpoint_auth_method": "none"
                        }
                    """.trimToFlatJson()
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "client_id": "clientId",
                                "application_type": "web",
                                "client_name": {
                                    "default": "Trixnity"
                                },
                                "client_uri": "https://client.example.com",
                                "grant_types": [
                                    "refresh_token",
                                    "authorization_code"
                                ],
                                "redirect_uris": [
                                    "https://localhost:8080/redirect"
                                ],
                                "response_types": [
                                    "code"
                                ],
                                "token_endpoint_auth_method": "none"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.registerClient(clientMetadata).getOrThrow() shouldBe ClientRegistrationResponse(
            clientId = "clientId",
            clientMetadata = clientMetadata
        )
    }

    @Test
    @OptIn(MSC4191::class)
    fun shouldGetToken() = runTest {
        println(
            Json.encodeToString(
                TokenResponse(
                    accessToken = "access",
                    refreshToken = "refresh",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    scope = setOf("scope1", "scope2")
                )
            )
        )
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/token"
                    request.body.toByteArray().decodeToString() shouldBe
                            "grant_type=authorization_code&code=CODE" +
                            "&redirect_uri=trixnity%3A%2F%2Fsso" +
                            "&client_id=clientId" +
                            "&code_verifier=CODE_VERIFIER"
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "access_token": "access",
                                "refresh_token": "refresh",
                                "token_type": "Bearer",
                                "expires_in": 3600,
                                "scope": "scope1 scope2"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.getToken(
            clientId = "clientId",
            redirectUri = "trixnity://sso",
            code = "CODE",
            codeVerifier = "CODE_VERIFIER"
        ).getOrThrow() shouldBe TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "Bearer",
            expiresIn = 3600,
            scope = setOf("scope1", "scope2")
        )
    }

    @Test
    @OptIn(MSC4191::class)
    fun shouldRefreshToken() = runTest {
        println(
            Json.encodeToString(
                TokenResponse(
                    accessToken = "access",
                    refreshToken = "refresh",
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    scope = setOf("scope1", "scope2")
                )
            )
        )
        val matrixRestClient = OAuth2ApiClient(
            serverMetadata = serverMetadata,
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    request.url.toString() shouldBe "https://auth.matrix.host/token"
                    request.body.toByteArray().decodeToString() shouldBe
                            "grant_type=refresh_token" +
                            "&refresh_token=refresh" +
                            "&client_id=clientId"
                    request.method shouldBe HttpMethod.Post

                    respond(
                        """
                            {
                                "access_token": "access",
                                "refresh_token": "refresh",
                                "token_type": "Bearer",
                                "expires_in": 3600,
                                "scope": "scope1 scope2"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            }
        )

        matrixRestClient.refreshToken(
            refreshToken = "refresh",
            clientId = "clientId",
        ).getOrThrow() shouldBe TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = "Bearer",
            expiresIn = 3600,
            scope = setOf("scope1", "scope2")
        )
    }
}