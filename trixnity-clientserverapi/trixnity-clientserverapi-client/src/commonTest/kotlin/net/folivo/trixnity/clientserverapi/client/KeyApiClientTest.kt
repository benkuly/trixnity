package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.clientserverapi.model.key.*
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.*
import net.folivo.trixnity.core.model.keys.KeyAlgorithm.Curve25519
import net.folivo.trixnity.core.model.keys.KeyAlgorithm.SignedCurve25519
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyApiClientTest : TrixnityBaseTest() {

    private val alice = UserId("@alice:example.com")

    @Test
    fun shouldSetDeviceKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/upload",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "device_keys":{
                                "user_id":"@alice:example.com",
                                "device_id":"JLAFKJWSCS",
                                "algorithms":[
                                  "m.olm.v1.curve25519-aes-sha2",
                                  "m.megolm.v1.aes-sha2"
                                ],
                                "keys":{
                                  "curve25519:JLAFKJWSCS":"3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI",
                                  "ed25519:JLAFKJWSCS":"lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI"
                                },
                                "signatures":{
                                  "@alice:example.com":{
                                    "ed25519:JLAFKJWSCS":"dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                  }
                                }
                              },
                              "one_time_keys":{
                                "curve25519:AAAAAQ":"/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8",
                                "signed_curve25519:AAAAHg":{
                                  "key":"zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:JLAFKJWSCS":"FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                    }
                                  }
                                },
                                "signed_curve25519:AAAAHQ":{
                                  "key":"j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw",
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:JLAFKJWSCS":"IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                                    }
                                  }
                                }
                              },
                              "fallback_keys":{
                                "signed_curve25519:AAAAHg":{
                                  "key":"zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                  "fallback":true,
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:JLAFKJWSCS":"FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                    }
                                  }
                                }
                              }
                            }
                    """.trimToFlatJson()
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
            },
        )
        val result = matrixRestClient.key.setKeys(
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
                    "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", signatures = mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            )
                        )
                    )
                ),
                SignedCurve25519Key(
                    "AAAAHQ", "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw", signatures = mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                            )
                        )
                    )
                )
            ),
            fallbackKeys = keysOf(
                SignedCurve25519Key(
                    "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", true, mapOf(
                        UserId("alice", "example.com") to keysOf(
                            Ed25519Key(
                                "JLAFKJWSCS",
                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            )
                        )
                    )
                )
            )
        ).getOrThrow()
        assertEquals<Map<KeyAlgorithm, Int>>(mapOf(Curve25519 to 10, SignedCurve25519 to 20), result)
    }

    @Test
    fun shouldGetKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/query",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "device_keys":{
                                    "@alice:example.com":[]
                                  },
                                  "timeout":10000
                                }
                    """.trimToFlatJson()
                    respond(
                        """
                                {
                                  "device_keys": {
                                    "@alice:example.com": {
                                      "JLAFKJWSCS": {
                                        "algorithms": [
                                          "m.olm.v1.curve25519-aes-sha2",
                                          "m.megolm.v1.aes-sha2"
                                        ],
                                        "device_id": "JLAFKJWSCS",
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
                                        },
                                        "user_id": "@alice:example.com"
                                      }
                                    }
                                  },
                                  "master_keys": {
                                    "@alice:example.com": {
                                      "keys": {
                                        "ed25519:base64+master+public+key": "base64+master+public+key"
                                      },
                                      "usage": [
                                        "master"
                                      ],
                                      "user_id": "@alice:example.com"
                                    }
                                  },
                                  "self_signing_keys": {
                                    "@alice:example.com": {
                                      "keys": {
                                        "ed25519:base64+self+signing+public+key": "base64+self+signing+public+key"
                                      },
                                      "signatures": {
                                        "@alice:example.com": {
                                          "ed25519:base64+master+public+key": "signature+of+self+signing+key"
                                        }
                                      },
                                      "usage": [
                                        "self_signing"
                                      ],
                                      "user_id": "@alice:example.com"
                                    }
                                  },
                                  "user_signing_keys": {
                                    "@alice:example.com": {
                                      "keys": {
                                        "ed25519:base64+user+signing+public+key": "base64+user+signing+public+key"
                                      },
                                      "signatures": {
                                        "@alice:example.com": {
                                          "ed25519:base64+master+public+key": "signature+of+user+signing+key"
                                        }
                                      },
                                      "usage": [
                                        "user_signing"
                                      ],
                                      "user_id": "@alice:example.com"
                                    }
                                  }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getKeys(
            timeout = 10_000,
            deviceKeys = mapOf(UserId("alice", "example.com") to setOf()),
        ).getOrThrow() shouldBe GetKeys.Response(
            failures = null,
            deviceKeys = mapOf(
                alice to mapOf(
                    "JLAFKJWSCS" to Signed(
                        signed = DeviceKeys(
                            userId = alice,
                            deviceId = "JLAFKJWSCS",
                            algorithms = setOf(Olm, Megolm),
                            keys = keysOf(
                                Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                            )
                        ),
                        signatures = mapOf(
                            alice to keysOf(
                                Ed25519Key(
                                    "JLAFKJWSCS",
                                    "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                )
                            )
                        ),
                    )
                )
            ),
            masterKeys = mapOf(
                alice to Signed(
                    signed = CrossSigningKeys(
                        userId = alice,
                        usage = setOf(MasterKey),
                        keys = keysOf(Ed25519Key("base64+master+public+key", "base64+master+public+key"))
                    ),
                )
            ),
            selfSigningKeys = mapOf(
                alice to Signed(
                    signed = CrossSigningKeys(
                        userId = alice,
                        usage = setOf(SelfSigningKey),
                        keys = keysOf(Ed25519Key("base64+self+signing+public+key", "base64+self+signing+public+key"))
                    ),
                    signatures = mapOf(
                        alice to keysOf(Ed25519Key("base64+master+public+key", "signature+of+self+signing+key"))
                    )
                )
            ),
            userSigningKeys = mapOf(
                alice to Signed(
                    signed = CrossSigningKeys(
                        userId = alice,
                        usage = setOf(UserSigningKey),
                        keys = keysOf(Ed25519Key("base64+user+signing+public+key", "base64+user+signing+public+key"))
                    ),
                    signatures = mapOf(
                        alice to keysOf(Ed25519Key("base64+master+public+key", "signature+of+user+signing+key"))
                    )
                )
            )
        )
    }

    @Test
    fun shouldQueryKeysAndSkipMalformedKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/query",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "device_keys":{
                                    "@alice:example.com":[]
                                  },
                                  "timeout":10000
                                }
                    """.trimToFlatJson()
                    respond(
                        """
                                {
                                  "device_keys": {
                                    "@alice:example.com": {
                                      "JLAFKJWSCS": {
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
                                        },
                                        "user_id": "@alice:example.com"
                                      }
                                    }
                                  },
                                  "master_keys": {
                                    "@alice:example.com": {
                                      "usage": [
                                        "master"
                                      ],
                                      "user_id": "@alice:example.com"
                                    }
                                  },
                                  "self_signing_keys": {
                                    "@alice:example.com": {
                                      "keys": {
                                        "ed25519:base64+self+signing+public+key": "base64+self+signing+public+key"
                                      },
                                      "signatures": {
                                        "@alice:example.com": {
                                          "ed25519:base64+master+public+key": "signature+of+self+signing+key"
                                        }
                                      },
                                      "usage": [
                                        "self_signing"
                                      ]
                                    }
                                  },
                                  "user_signing_keys": {
                                    "@alice:example.com": {
                                      "keys": {
                                        "ed25519:base64+user+signing+public+key": "base64+user+signing+public+key"
                                      },
                                      "signatures": {
                                        "@alice:example.com": {
                                          "ed25519:base64+master+public+key": "signature+of+user+signing+key"
                                        }
                                      },
                                      "user_id": "@alice:example.com"
                                    }
                                  }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getKeys(
            timeout = 10_000,
            deviceKeys = mapOf(UserId("alice", "example.com") to setOf()),
        ).getOrThrow() shouldBe GetKeys.Response(
            failures = null,
            deviceKeys = mapOf(
                alice to mapOf()
            ),
            masterKeys = mapOf(),
            selfSigningKeys = mapOf(),
            userSigningKeys = mapOf()
        )
    }

    @Test
    fun shouldClaimKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/claim",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                                {
                                  "one_time_keys":{
                                    "@alice:example.com":{
                                      "JLAFKJWSCS":"signed_curve25519"
                                    }
                                  },
                                  "timeout":10000
                                }
                    """.trimToFlatJson()
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
            },
        )
        val result = matrixRestClient.key.claimKeys(
            timeout = 10_000,
            oneTimeKeys = mapOf(UserId("alice", "example.com") to mapOf("JLAFKJWSCS" to SignedCurve25519))
        ).getOrThrow()
        assertEquals(mapOf(), result.failures)
        val oneTimeKeyMap = result.oneTimeKeys.entries.firstOrNull()
        assertEquals(UserId("alice", "example.com"), oneTimeKeyMap?.key)
        val oneTimeKey = oneTimeKeyMap?.value?.entries?.firstOrNull()
        assertEquals("JLAFKJWSCS", oneTimeKey?.key)
        assertEquals(1, oneTimeKey?.value?.size)
    }

    @Test
    fun shouldGetKeyChanges() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/changes?from=s72594_4483_1934&to=s75689_5632_2435",
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
            },
        )
        val result = matrixRestClient.key.getKeyChanges(
            from = "s72594_4483_1934",
            to = "s75689_5632_2435"
        ).getOrThrow()
        assertEquals(
            GetKeyChanges.Response(
                changed = setOf(UserId("@alice:example.com"), UserId("@bob:example.org")),
                left = setOf(UserId("@clara:example.com"), UserId("@doug:example.org"))
            ), result
        )
    }

    @Test
    fun shouldSetCrossSigningKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/device_signing/upload",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                            {
                              "master_key":{
                                "user_id":"@alice:example.com",
                                "usage":["master"],
                                "keys":{
                                  "ed25519:alice+base64+public+key":"alice+base64+public+key"
                                },
                                "signatures":{
                                  "@alice:example.com":{
                                    "ed25519:alice+base64+master+key":"signature+of+key"
                                  }
                                }
                              },
                              "self_signing_key":{
                                "user_id":"@alice:example.com",
                                "usage":["self_signing"],
                                "keys":{
                                  "ed25519:alice+base64+public+key":"alice+base64+public+key"
                                },
                                "signatures":{
                                  "@alice:example.com":{
                                    "ed25519:alice+base64+master+key":"signature+of+key",
                                    "ed25519:base64+master+public+key":"signature+of+self+signing+key"
                                  }
                                }
                              },
                              "user_signing_key":{
                                "user_id":"@alice:example.com",
                                "usage":["user_signing"],
                                "keys":{
                                  "ed25519:alice+base64+public+key":"alice+base64+public+key"
                                },
                                "signatures":{
                                  "@alice:example.com":{
                                    "ed25519:alice+base64+master+key":"signature+of+key",
                                    "ed25519:base64+master+public+key":"signature+of+user+signing+key"
                                  }
                                }
                              }
                            }
                    """.trimToFlatJson()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setCrossSigningKeys(
            masterKey = Signed(
                CrossSigningKeys(
                    keys = keysOf(Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                    usage = setOf(MasterKey),
                    userId = UserId("@alice:example.com"),
                ),
                mapOf(
                    UserId("@alice:example.com") to keysOf(
                        Ed25519Key("alice+base64+master+key", "signature+of+key")
                    )
                )
            ),
            selfSigningKey = Signed(
                CrossSigningKeys(
                    keys = keysOf(Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                    usage = setOf(SelfSigningKey),
                    userId = UserId("@alice:example.com"),
                ),
                mapOf(
                    UserId("@alice:example.com") to keysOf(
                        Ed25519Key("alice+base64+master+key", "signature+of+key"),
                        Ed25519Key("base64+master+public+key", "signature+of+self+signing+key")
                    )
                )
            ),
            userSigningKey = Signed(
                CrossSigningKeys(
                    keys = keysOf(Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                    usage = setOf(UserSigningKey),
                    userId = UserId("@alice:example.com"),
                ),
                mapOf(
                    UserId("@alice:example.com") to keysOf(
                        Ed25519Key("alice+base64+master+key", "signature+of+key"),
                        Ed25519Key("base64+master+public+key", "signature+of+user+signing+key")
                    )
                )
            ),
        ).getOrThrow()
    }

    @Test
    fun shouldAddSignatures() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/keys/signatures/upload",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
                           {
                              "@alice:example.com":{
                                "HIJKLMN":{
                                  "user_id":"@alice:example.com",
                                  "device_id":"HIJKLMN",
                                  "algorithms":[
                                    "m.olm.v1.curve25519-aes-sha2",
                                    "m.megolm.v1.aes-sha2"
                                  ],
                                  "keys":{
                                    "ed25519:HIJKLMN":"base64+ed25519+key",
                                    "curve25519:HIJKLMN":"base64+curve25519+key"
                                  },
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:base64+self+signing+public+key":"base64+signature+of+HIJKLMN"
                                    }
                                  }
                                },
                                "base64+master+public+key":{
                                  "user_id":"@alice:example.com",
                                  "usage":["master"],
                                  "keys":{
                                    "ed25519:base64+master+public+key":"base64+master+public+key"
                                  },
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:HIJKLMN":"base64+signature+of+master+key"
                                    }
                                  }
                                }
                              },
                              "@bob:example.com":{
                                "bobs+base64+master+public+key":{
                                  "user_id":"@bob:example.com",
                                  "usage":["master"],
                                  "keys":{
                                    "ed25519:bobs+base64+master+public+key":"bobs+base64+master+public+key"
                                  },
                                  "signatures":{
                                    "@alice:example.com":{
                                      "ed25519:base64+user+signing+public+key":"base64+signature+of+bobs+master+key"
                                    }
                                  }
                                }
                              }
                            }
                    """.trimToFlatJson()
                    respond(
                        """
                                {
                                  "failures": {
                                    "@alice:example.com": {
                                      "HIJKLMN": {
                                        "errcode": "M_INVALID_SIGNATURE",
                                        "error": "Invalid signature"
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        val result = matrixRestClient.key.addSignatures(
            signedCrossSigningKeys = setOf(
                Signed(
                    CrossSigningKeys(
                        keys = keysOf(Ed25519Key("base64+master+public+key", "base64+master+public+key")),
                        usage = setOf(MasterKey),
                        userId = UserId("@alice:example.com"),
                    ),
                    mapOf(
                        UserId("@alice:example.com") to keysOf(
                            Ed25519Key("HIJKLMN", "base64+signature+of+master+key")
                        )
                    )
                ),
                Signed(
                    CrossSigningKeys(
                        keys = keysOf(Ed25519Key("bobs+base64+master+public+key", "bobs+base64+master+public+key")),
                        usage = setOf(MasterKey),
                        userId = UserId("@bob:example.com"),
                    ),
                    mapOf(
                        UserId("@alice:example.com") to keysOf(
                            Ed25519Key("base64+user+signing+public+key", "base64+signature+of+bobs+master+key")
                        )
                    )
                )
            ),
            signedDeviceKeys = setOf(
                Signed(
                    DeviceKeys(
                        userId = UserId("@alice:example.com"),
                        deviceId = "HIJKLMN",
                        keys = keysOf(
                            Ed25519Key("HIJKLMN", "base64+ed25519+key"),
                            Curve25519Key("HIJKLMN", "base64+curve25519+key")
                        ),
                        algorithms = setOf(Olm, Megolm)
                    ),
                    mapOf(
                        UserId("@alice:example.com") to keysOf(
                            Ed25519Key("base64+self+signing+public+key", "base64+signature+of+HIJKLMN")
                        )
                    )
                )
            )
        ).getOrThrow()
        assertEquals(
            AddSignatures.Response(
                mapOf(
                    UserId("@alice:example.com") to mapOf(
                        "HIJKLMN" to JsonObject(
                            mapOf(
                                "errcode" to JsonPrimitive("M_INVALID_SIGNATURE"),
                                "error" to JsonPrimitive("Invalid signature")
                            )
                        )
                    )
                )
            ), result
        )
    }

    @Test
    fun shouldGetRoomKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "rooms": {
                                    "!room:example.org": {
                                      "sessions": {
                                        "+ess/ionId1": {
                                          "first_message_index": 1,
                                          "forwarded_count": 0,
                                          "is_verified": true,
                                          "session_data": {
                                            "ciphertext": "base64+ciphertext+of+JSON+data",
                                            "ephemeral": "base64+ephemeral+key",
                                            "mac": "base64+mac+of+ciphertext"
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
            },
        )
        matrixRestClient.key.getRoomKeys("1").getOrThrow()
            .shouldBe(
                RoomsKeyBackup(
                    mapOf(
                        RoomId("!room:example.org") to RoomKeyBackup(
                            mapOf(
                                "+ess/ionId1" to RoomKeyBackupData(
                                    firstMessageIndex = 1,
                                    forwardedCount = 0,
                                    isVerified = true,
                                    sessionData = EncryptedRoomKeyBackupV1SessionData(
                                        ciphertext = "base64+ciphertext+of+JSON+data",
                                        ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                                        mac = "base64+mac+of+ciphertext"
                                    )
                                )
                            )
                        )
                    )
                )
            )
    }

    @Test
    fun shouldGetRoomKeysFromRoom() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "sessions": {
                                    "+ess/ionId1": {
                                      "first_message_index": 1,
                                      "forwarded_count": 0,
                                      "is_verified": true,
                                      "session_data": {
                                        "ciphertext": "base64+ciphertext+of+JSON+data",
                                        "ephemeral": "base64+ephemeral+key",
                                        "mac": "base64+mac+of+ciphertext"
                                      }
                                    }
                                  }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getRoomKeys("1", RoomId("!room:example.org"))
            .getOrThrow()
            .shouldBe(
                RoomKeyBackup(
                    mapOf(
                        "+ess/ionId1" to RoomKeyBackupData(
                            firstMessageIndex = 1,
                            forwardedCount = 0,
                            isVerified = true,
                            sessionData = EncryptedRoomKeyBackupV1SessionData(
                                ciphertext = "base64+ciphertext+of+JSON+data",
                                ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                                mac = "base64+mac+of+ciphertext"
                            )
                        )
                    )
                )
            )
    }

    @Test
    fun shouldGetRoomKeysFromRoomAndSession() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org/+ess%2FionId1?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "first_message_index": 1,
                                  "forwarded_count": 0,
                                  "is_verified": true,
                                  "session_data": {
                                    "ciphertext": "base64+ciphertext+of+JSON+data",
                                    "ephemeral": "base64+ephemeral+key",
                                    "mac": "base64+mac+of+ciphertext"
                                  }
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getRoomKeys(
            "1", RoomId("!room:example.org"), "+ess/ionId1"
        ).getOrThrow()
            .shouldBe(
                RoomKeyBackupData(
                    firstMessageIndex = 1,
                    forwardedCount = 0,
                    isVerified = true,
                    sessionData = EncryptedRoomKeyBackupV1SessionData(
                        ciphertext = "base64+ciphertext+of+JSON+data",
                        ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                        mac = "base64+mac+of+ciphertext"
                    )
                )
            )
    }

    @Test
    fun shouldSetRoomKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString().shouldBe(
                        """
                                {
                                  "rooms":{
                                    "!room:example.org":{
                                      "sessions":{
                                        "+ess/ionId1":{
                                          "first_message_index":1,
                                          "forwarded_count":0,
                                          "is_verified":true,
                                          "session_data":{
                                            "ciphertext":"base64+ciphertext+of+JSON+data",
                                            "ephemeral":"base64+ephemeral+key",
                                            "mac":"base64+mac+of+ciphertext"
                                          }
                                        }
                                      }
                                    }
                                  }
                                }
                            """.trimToFlatJson()
                    )
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setRoomKeys(
            "1",
            RoomsKeyBackup(
                mapOf(
                    RoomId("!room:example.org") to RoomKeyBackup(
                        mapOf(
                            "+ess/ionId1" to RoomKeyBackupData(
                                firstMessageIndex = 1,
                                forwardedCount = 0,
                                isVerified = true,
                                sessionData = EncryptedRoomKeyBackupV1SessionData(
                                    ciphertext = "base64+ciphertext+of+JSON+data",
                                    ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                                    mac = "base64+mac+of+ciphertext"
                                )
                            )
                        )
                    )
                )
            )
        ).getOrThrow()
            .shouldBe(
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldSetRoomKeysWithRoom() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString().shouldBe(
                        """
                                {
                                  "sessions":{
                                    "+ess/ionId1":{
                                      "first_message_index":1,
                                      "forwarded_count":0,
                                      "is_verified":true,
                                      "session_data":{
                                        "ciphertext":"base64+ciphertext+of+JSON+data",
                                        "ephemeral":"base64+ephemeral+key",
                                        "mac":"base64+mac+of+ciphertext"
                                      }
                                    }
                                  }
                                }
                            """.trimToFlatJson()
                    )
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setRoomKeys(
            "1",
            RoomId("!room:example.org"),
            RoomKeyBackup(
                mapOf(
                    "+ess/ionId1" to RoomKeyBackupData(
                        firstMessageIndex = 1,
                        forwardedCount = 0,
                        isVerified = true,
                        sessionData = EncryptedRoomKeyBackupV1SessionData(
                            ciphertext = "base64+ciphertext+of+JSON+data",
                            ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                            mac = "base64+mac+of+ciphertext"
                        )
                    )
                )
            )
        ).getOrThrow()
            .shouldBe(
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldSetRoomKeysWithRoomAndSession() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org/+ess%2FionId1?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString().shouldBe(
                        """
                                {
                                  "first_message_index":1,
                                  "forwarded_count":0,
                                  "is_verified":true,
                                  "session_data":{
                                    "ciphertext":"base64+ciphertext+of+JSON+data",
                                    "ephemeral":"base64+ephemeral+key",
                                    "mac":"base64+mac+of+ciphertext"
                                  }
                                }
                            """.trimToFlatJson()
                    )
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setRoomKeys(
            "1",
            RoomId("!room:example.org"),
            "+ess/ionId1",
            RoomKeyBackupData(
                firstMessageIndex = 1,
                forwardedCount = 0,
                isVerified = true,
                sessionData = EncryptedRoomKeyBackupV1SessionData(
                    ciphertext = "base64+ciphertext+of+JSON+data",
                    ephemeral = Curve25519KeyValue("base64+ephemeral+key"),
                    mac = "base64+mac+of+ciphertext"
                )
            )
        ).getOrThrow()
            .shouldBe(
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldDeleteRoomKeys() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.deleteRoomKeys("1").getOrThrow()
            .shouldBe(
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldDeleteRoomKeysFromRoom() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.deleteRoomKeys("1", RoomId("!room:example.org"))
            .getOrThrow()
            .shouldBe(
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldDeleteRoomKeysFromRoomAndSession() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/keys/!room:example.org/+ess%2FionId1?version=1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "count": 10,
                                  "etag": "abcdefg"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.deleteRoomKeys(
            "1", RoomId("!room:example.org"), "+ess/ionId1"
        ).getOrThrow()
            .shouldBe(
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            )
    }

    @Test
    fun shouldGetRoomKeysVersion() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/version",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "algorithm": "m.megolm_backup.v1.curve25519-aes-sha2",
                                  "auth_data": {
                                    "public_key": "abcdefg",
                                    "signatures": {
                                      "@alice:example.org": {
                                        "ed25519:deviceid": "signature"
                                      }
                                    }
                                  },
                                  "count": 42,
                                  "etag": "anopaquestring",
                                  "version": "1"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getRoomKeysVersion().getOrThrow()
            .shouldBe(
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Curve25519KeyValue("abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Ed25519Key("deviceid", "signature"))
                        )
                    ),
                    count = 42,
                    etag = "anopaquestring",
                    version = "1"
                )
            )
    }

    @Test
    fun shouldGetRoomKeysVersionFromVersion() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/version/1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        """
                                {
                                  "algorithm": "m.megolm_backup.v1.curve25519-aes-sha2",
                                  "auth_data": {
                                    "public_key": "abcdefg",
                                    "signatures": {
                                      "@alice:example.org": {
                                        "ed25519:deviceid": "signature"
                                      }
                                    }
                                  },
                                  "count": 42,
                                  "etag": "anopaquestring",
                                  "version": "1"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.getRoomKeysVersion("1").getOrThrow()
            .shouldBe(
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Curve25519KeyValue("abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Ed25519Key("deviceid", "signature"))
                        )
                    ),
                    count = 42,
                    etag = "anopaquestring",
                    version = "1"
                )
            )
    }

    @Test
    fun shouldSetRoomKeysVersion() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/version",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString().shouldBe(
                        """
                                {
                                  "auth_data":{
                                    "public_key":"abcdefg",
                                    "signatures":{
                                      "@alice:example.org":{
                                        "ed25519:deviceid":"signature"
                                      }
                                    }
                                  },
                                  "algorithm":"m.megolm_backup.v1.curve25519-aes-sha2"
                                }
                            """.trimToFlatJson()
                    )
                    respond(
                        """
                                {
                                  "version": "1"
                                }
                            """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setRoomKeysVersion(
            SetRoomKeyBackupVersionRequest.V1(
                authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                    publicKey = Curve25519KeyValue("abcdefg"),
                    signatures = mapOf(
                        UserId("@alice:example.org") to keysOf(Ed25519Key("deviceid", "signature"))
                    )
                )
            )
        ).getOrThrow().shouldBe("1")
    }

    @Test
    fun shouldSetRoomKeysVersionWithVersion() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/version/1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    request.body.toByteArray().decodeToString().shouldBe(
                        """
                                {
                                  "auth_data":{
                                    "public_key":"abcdefg",
                                    "signatures":{
                                      "@alice:example.org":{
                                        "ed25519:deviceid":"signature"
                                      }
                                    }
                                  },
                                  "version":"1",
                                  "algorithm":"m.megolm_backup.v1.curve25519-aes-sha2"
                                }
                            """.trimToFlatJson()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.setRoomKeysVersion(
            SetRoomKeyBackupVersionRequest.V1(
                authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                    publicKey = Curve25519KeyValue("abcdefg"),
                    signatures = mapOf(
                        UserId("@alice:example.org") to keysOf(Ed25519Key("deviceid", "signature"))
                    )
                ),
                version = "1"
            )
        ).getOrThrow()
            .shouldBe("1")
    }

    @Test
    fun shouldDeleteRoomKeysVersion() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/room_keys/version/1",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    request.body.toByteArray().decodeToString().shouldBeEmpty()
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            },
        )
        matrixRestClient.key.deleteRoomKeysVersion("1").getOrThrow()
    }
}