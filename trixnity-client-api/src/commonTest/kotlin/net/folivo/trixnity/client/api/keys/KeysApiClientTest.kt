package net.folivo.trixnity.client.api.keys

import io.kotest.assertions.json.shouldEqualJson
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.runBlockingTest
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.*
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm.Curve25519
import net.folivo.trixnity.core.model.crypto.KeyAlgorithm.SignedCurve25519
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.core.model.crypto.keysOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KeysApiClientTest {

    @Test
    fun shouldUploadKeys() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/keys/upload",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                            {
                              "device_keys": {
                                "user_id": "@alice:example.com",
                                "device_id": "JLAFKJWSCS",
                                "algorithms": [
                                  "m.olm.v1.curve25519-aes-sha2",
                                  "m.megolm.v1.aes-sha2"
                                ],
                                "keys": {
                                  "curve25519:JLAFKJWSCS": "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI",
                                  "ed25519:JLAFKJWSCS": "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI"
                                },
                                "signatures": {
                                  "@alice:example.com": {
                                    "ed25519:JLAFKJWSCS": "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                  }
                                }
                              },
                              "one_time_keys": {
                                "curve25519:AAAAAQ": "/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8",
                                "signed_curve25519:AAAAHg": {
                                  "key": "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                  "signatures": {
                                    "@alice:example.com": {
                                      "ed25519:JLAFKJWSCS": "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                    }
                                  }
                                },
                                "signed_curve25519:AAAAHQ": {
                                  "key": "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw",
                                  "signatures": {
                                    "@alice:example.com": {
                                      "ed25519:JLAFKJWSCS": "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                                    }
                                  }
                                }
                              }
                            }
                    """.trimIndent()
                        )
                        respond(
                            """
                                {
                                  "one_time_key_counts": {
                                    "curve25519": 10,
                                    "signed_curve25519": 20
                                  }
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
        )
        val result = matrixRestClient.keys.uploadKeys(
            deviceKeys = Signed(
                DeviceKeys(
                    userId = UserId("alice", "example.com"),
                    deviceId = "JLAFKJWSCS",
                    algorithms = setOf(Olm, Megolm),
                    keys = keysOf(
                        Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                        Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                    )
                ),
                mapOf(
                    UserId("alice", "example.com") to keysOf(
                        Ed25519Key(
                            "JLAFKJWSCS",
                            "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                        )
                    )
                )
            ),
            oneTimeKeys = keysOf(
                Curve25519Key("AAAAAQ", "/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8"),
                SignedCurve25519Key(
                    "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            )
                        )
                    )
                ),
                SignedCurve25519Key(
                    "AAAAHQ", "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw", mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                            )
                        )
                    )
                )
            )
        )
        assertEquals(mapOf(Curve25519 to 10, SignedCurve25519 to 20), result)
    }

    @Test
    fun shouldQueryKeys() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/keys/query",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                                {
                                  "timeout": 10000,
                                  "device_keys": {
                                    "@alice:example.com": []
                                  },
                                  "token": "string"
                                }
                    """.trimIndent()
                        )
                        respond(
                            """
                                {
                                  "failures": {},
                                  "device_keys": {
                                    "@alice:example.com": {
                                      "JLAFKJWSCS": {
                                        "user_id": "@alice:example.com",
                                        "device_id": "JLAFKJWSCS",
                                        "algorithms": [
                                          "m.olm.v1.curve25519-aes-sha2",
                                          "m.megolm.v1.aes-sha2"
                                        ],
                                        "keys": {
                                          "curve25519:JLAFKJWSCS": "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI",
                                          "ed25519:JLAFKJWSCS": "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI"
                                        },
                                        "signatures": {
                                          "@alice:example.com": {
                                            "ed25519:JLAFKJWSCS": "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                          }
                                        },
                                        "unsigned": {
                                          "device_display_name": "Alice's mobile phone"
                                        }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
        )
        val result = matrixRestClient.keys.getKeys(
            timeout = 10_000,
            deviceKeys = mapOf(UserId("alice", "example.com") to setOf()),
            token = "string"
        )
        assertEquals(mapOf(), result.failures)
        val oneTimeKeyMap = result.deviceKeys?.entries?.firstOrNull()
        assertEquals(UserId("alice", "example.com"), oneTimeKeyMap?.key)
        val oneTimeKey = oneTimeKeyMap?.value?.entries?.firstOrNull()
        assertEquals("JLAFKJWSCS", oneTimeKey?.key)
        assertNotNull(oneTimeKey?.value)
    }

    @Test
    fun shouldClaimKeys() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/keys/claim",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Post, request.method)
                        request.body.toByteArray().decodeToString().shouldEqualJson(
                            """
                                {
                                  "timeout": 10000,
                                  "one_time_keys": {
                                    "@alice:example.com": {
                                      "JLAFKJWSCS": "signed_curve25519"
                                    }
                                  }
                                }
                    """.trimIndent()
                        )
                        respond(
                            """
                                {
                                  "failures": {},
                                  "one_time_keys": {
                                    "@alice:example.com": {
                                      "JLAFKJWSCS": {
                                        "signed_curve25519:AAAAHg": {
                                          "key": "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                          "signatures": {
                                            "@alice:example.com": {
                                              "ed25519:JLAFKJWSCS": "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
        )
        val result = matrixRestClient.keys.claimKeys(
            timeout = 10_000,
            oneTimeKeys = mapOf(UserId("alice", "example.com") to mapOf("JLAFKJWSCS" to SignedCurve25519))
        )
        assertEquals(mapOf(), result.failures)
        val oneTimeKeyMap = result.oneTimeKeys.entries.firstOrNull()
        assertEquals(UserId("alice", "example.com"), oneTimeKeyMap?.key)
        val oneTimeKey = oneTimeKeyMap?.value?.entries?.firstOrNull()
        assertEquals("JLAFKJWSCS", oneTimeKey?.key)
        assertEquals(1, oneTimeKey?.value?.size)
    }

    @Test
    fun shouldGetKeyChanges() = runBlockingTest {
        val matrixRestClient = MatrixApiClient(
            baseUrl = Url("https://matrix.host"),
            baseHttpClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        assertEquals(
                            "/_matrix/client/r0/keys/changes?from=s72594_4483_1934&to=s75689_5632_2435",
                            request.url.fullPath
                        )
                        assertEquals(HttpMethod.Get, request.method)
                        respond(
                            """
                                {
                                  "changed": [
                                    "@alice:example.com",
                                    "@bob:example.org"
                                  ],
                                  "left": [
                                    "@clara:example.com",
                                    "@doug:example.org"
                                  ]
                                }
                            """.trimIndent(),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, Application.Json.toString())
                        )
                    }
                }
            },
        )
        val result = matrixRestClient.keys.getKeyChanges(
            from = "s72594_4483_1934",
            to = "s75689_5632_2435"
        )
        assertEquals(
            GetKeyChangesResponse(
                changed = setOf(UserId("@alice:example.com"), UserId("@bob:example.org")),
                left = setOf(UserId("@clara:example.com"), UserId("@doug:example.org"))
            ), result
        )
    }
}