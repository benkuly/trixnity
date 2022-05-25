package net.folivo.trixnity.serverserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.serverserverapi.model.discovery.*
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoveryApiClientTest {
    @Test
    fun shouldGetWellKnown() = runTest {
        val matrixRestClient = MatrixServerServerApiClient(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            getRoomVersion = { "3" },
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/.well-known/matrix/server", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "m.server": "delegated.example.com:1234"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getWellKnown()
            .getOrThrow() shouldBe GetWellKnown.Response("delegated.example.com:1234")
    }

    @Test
    fun shouldGetServerVersion() = runTest {
        val matrixRestClient = MatrixServerServerApiClient(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            getRoomVersion = { "3" },
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/version", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "server": {
                                "name": "My_Homeserver_Implementation",
                                "version": "ArbitraryVersionNumber"
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getServerVersion()
            .getOrThrow() shouldBe GetServerVersion.Response(
            GetServerVersion.Response.Server(
                name = "My_Homeserver_Implementation",
                version = "ArbitraryVersionNumber"
            )
        )
    }

    @Test
    fun shouldGetServerKeys() = runTest {
        val matrixRestClient = MatrixServerServerApiClient(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            getRoomVersion = { "3" },
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/key/v2/server", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.getServerKeys()
            .getOrThrow() shouldBe Signed(
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
    }

    @Test
    fun shouldQueryServerKeys() = runTest {
        val matrixRestClient = MatrixServerServerApiClient(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            getRoomVersion = { "3" },
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/key/v2/query", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
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
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.queryServerKeys(
            QueryServerKeys.Request(buildJsonObject {
                put("example.org", buildJsonObject {
                    put("ed25519:abc123", buildJsonObject {
                        put("minimum_valid_until_ts", JsonPrimitive(1234567890))
                    })
                })
            })
        ).getOrThrow() shouldBe QueryServerKeysResponse(
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
    }

    @Test
    fun shouldQueryServerKeysByServer() = runTest {
        val matrixRestClient = MatrixServerServerApiClient(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            getRoomVersion = { "3" },
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/key/v2/query/example.org?minimum_valid_until_ts=1234567890",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.discovery.queryKeysByServer("example.org", 1234567890)
            .getOrThrow() shouldBe QueryServerKeysResponse(
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
    }
}