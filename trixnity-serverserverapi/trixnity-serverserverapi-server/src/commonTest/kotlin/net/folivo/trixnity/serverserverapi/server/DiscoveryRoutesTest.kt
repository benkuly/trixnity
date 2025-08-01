package net.folivo.trixnity.serverserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.serverserverapi.model.discovery.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class DiscoveryRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixDataUnitJson(TestRoomVersionStore("12"))
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<DiscoveryApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixSignatureAuth(hostname = "") {
                authenticationFunction = { SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                discoveryApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetWellKnown() = testApplication {
        initCut()
        everySuspend { handlerMock.getWellKnown(any()) }
            .returns(
                GetWellKnown.Response("delegated.example.com:1234")
            )
        val response = client.get("/.well-known/matrix/server")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "m.server": "delegated.example.com:1234"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getWellKnown(any())
        }
    }

    @Test
    fun shouldGetServerVersion() = testApplication {
        initCut()
        everySuspend { handlerMock.getServerVersion(any()) }
            .returns(
                GetServerVersion.Response(
                    GetServerVersion.Response.Server(
                        name = "My_Homeserver_Implementation",
                        version = "ArbitraryVersionNumber"
                    )
                )
            )
        val response = client.get("/_matrix/federation/v1/version")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "server": {
                        "name": "My_Homeserver_Implementation",
                        "version": "ArbitraryVersionNumber"
                      }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getServerVersion(any())
        }
    }

    @Test
    fun shouldGetServerKeys() = testApplication {
        initCut()
        everySuspend { handlerMock.getServerKeys(any()) }
            .returns(
                Signed(
                    ServerKeys(
                        serverName = "example.org",
                        validUntil = 1652262000000,
                        oldVerifyKeys = mapOf(
                            "ed25519:0ldk3y" to ServerKeys.OldVerifyKey(
                                expiredAt = 1532645052628,
                                keyValue = "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg"
                            )
                        ),
                        verifyKeys = mapOf(
                            "ed25519:abc123" to ServerKeys.VerifyKey(
                                keyValue = "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                            )
                        )
                    ),
                    mapOf(
                        "example.org" to keysOf(
                            Key.Ed25519Key("auto2", "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU")
                        )
                    )
                )
            )
        val response = client.get("/_matrix/key/v2/server")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "server_name": "example.org",
                      "valid_until_ts": 1652262000000,
                      "old_verify_keys": {
                        "ed25519:0ldk3y": {
                          "key": "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg",
                          "expired_ts": 1532645052628
                        }
                      },
                      "verify_keys": {
                        "ed25519:abc123": {
                          "key": "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                        }
                      },
                      "signatures": {
                        "example.org": {
                          "ed25519:auto2": "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU"
                        }
                      }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getServerKeys(any())
        }
    }

    @Test
    fun shouldQueryServerKeys() = testApplication {
        initCut()
        everySuspend { handlerMock.queryServerKeys(any()) }
            .returns(
                QueryServerKeysResponse(
                    setOf(
                        Signed(
                            ServerKeys(
                                serverName = "example.org",
                                validUntil = 1652262000000,
                                oldVerifyKeys = mapOf(
                                    "ed25519:0ldk3y" to ServerKeys.OldVerifyKey(
                                        expiredAt = 1532645052628,
                                        keyValue = "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg"
                                    )
                                ),
                                verifyKeys = mapOf(
                                    "ed25519:abc123" to ServerKeys.VerifyKey(
                                        keyValue = "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                                    )
                                )
                            ),
                            mapOf(
                                "example.org" to keysOf(
                                    Key.Ed25519Key("auto2", "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU")
                                )
                            )
                        )
                    )
                )
            )
        val response = client.post("/_matrix/key/v2/query") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "server_keys": {
                    "example.org": {
                      "ed25519:abc123": {
                        "minimum_valid_until_ts": 1234567890
                      }
                    }
                  }
                }

            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "server_keys": [
                        {
                          "server_name": "example.org",
                          "valid_until_ts": 1652262000000,
                          "old_verify_keys": {
                            "ed25519:0ldk3y": {
                              "key": "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg",
                              "expired_ts": 1532645052628
                            }
                          },
                          "verify_keys": {
                            "ed25519:abc123": {
                              "key": "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                            }
                          },
                          "signatures": {
                            "example.org": {
                              "ed25519:auto2": "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU"
                            }
                          }
                        }
                      ]
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.queryServerKeys(assert {
                it.requestBody shouldBe QueryServerKeys.Request(buildJsonObject {
                    put("example.org", buildJsonObject {
                        put("ed25519:abc123", buildJsonObject {
                            put("minimum_valid_until_ts", JsonPrimitive(1234567890))
                        })
                    })
                })
            })
        }
    }

    @Test
    fun shouldQueryServerKeysByServer() = testApplication {
        initCut()
        everySuspend { handlerMock.queryKeysByServer(any()) }
            .returns(
                QueryServerKeysResponse(
                    setOf(
                        Signed(
                            ServerKeys(
                                serverName = "example.org",
                                validUntil = 1652262000000,
                                oldVerifyKeys = mapOf(
                                    "ed25519:0ldk3y" to ServerKeys.OldVerifyKey(
                                        expiredAt = 1532645052628,
                                        keyValue = "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg"
                                    )
                                ),
                                verifyKeys = mapOf(
                                    "ed25519:abc123" to ServerKeys.VerifyKey(
                                        keyValue = "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                                    )
                                )
                            ),
                            mapOf(
                                "example.org" to keysOf(
                                    Key.Ed25519Key("auto2", "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU")
                                )
                            )
                        )
                    )
                )
            )
        val response = client.get("/_matrix/key/v2/query/example.org?minimum_valid_until_ts=1234567890")
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "server_keys": [
                        {
                          "server_name": "example.org",
                          "valid_until_ts": 1652262000000,
                          "old_verify_keys": {
                            "ed25519:0ldk3y": {
                              "key": "VGhpcyBzaG91bGQgYmUgeW91ciBvbGQga2V5J3MgZWQyNTUxOSBwYXlsb2FkLg",
                              "expired_ts": 1532645052628
                            }
                          },
                          "verify_keys": {
                            "ed25519:abc123": {
                              "key": "VGhpcyBzaG91bGQgYmUgYSByZWFsIGVkMjU1MTkgcGF5bG9hZA"
                            }
                          },
                          "signatures": {
                            "example.org": {
                              "ed25519:auto2": "VGhpcyBzaG91bGQgYWN0dWFsbHkgYmUgYSBzaWduYXR1cmU"
                            }
                          }
                        }
                      ]
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.queryKeysByServer(assert {
                it.endpoint.serverName shouldBe "example.org"
                it.endpoint.minimumValidUntil shouldBe 1234567890
            })
        }
    }
}