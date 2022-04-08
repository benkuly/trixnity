package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.keys.*
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeysRoutesTest {
    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()
    private val alice = UserId("alice", "example.com")

    @OptIn(ConfigurationApi::class)
    @Mock
    val handlerMock = configure(mock(classOf<KeysApiHandler>())) { stubsUnitByDefault = true }

    private fun ApplicationTestBuilder.initCut() {
        application {
            install(Authentication) {
                matrixAccessTokenAuth {
                    authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
                }
            }
            matrixApiServer(json) {
                routing {
                    keysApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @AfterTest
    fun afterTest() {
        verify(handlerMock).hasNoUnmetExpectations()
        verify(handlerMock).hasNoUnverifiedExpectations()
    }

    @Test
    fun shouldSetDeviceKeys() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setKeys)
            .whenInvokedWith(any())
            .then {
                SetKeys.Response((mapOf(KeyAlgorithm.Curve25519 to 10, KeyAlgorithm.SignedCurve25519 to 20)))
            }
        val response = client.post("/_matrix/client/v3/keys/upload") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
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
                  "one_time_key_counts":{
                    "curve25519":10,
                    "signed_curve25519":20
                  }
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::setKeys)
            .with(matching {
                it.requestBody shouldBe SetKeys.Request(
                    deviceKeys = Signed(
                        DeviceKeys(
                            userId = UserId("alice", "example.com"),
                            deviceId = "JLAFKJWSCS",
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = keysOf(
                                Key.Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                Key.Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                            )
                        ),
                        mapOf(
                            UserId("alice", "example.com") to keysOf(
                                Key.Ed25519Key(
                                    "JLAFKJWSCS",
                                    "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                )
                            )
                        )
                    ),
                    oneTimeKeys = keysOf(
                        Key.Curve25519Key("AAAAAQ", "/qyvZvwjiTxGdGU0RCguDCLeR+nmsb3FfNG3/Ve4vU8"),
                        Key.SignedCurve25519Key(
                            "AAAAHg", "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs", mapOf(
                                UserId("alice", "example.com") to keysOf(
                                    Key.Ed25519Key(
                                        "JLAFKJWSCS",
                                        "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                    )
                                )
                            )
                        ),
                        Key.SignedCurve25519Key(
                            "AAAAHQ", "j3fR3HemM16M7CWhoI4Sk5ZsdmdfQHsKL1xuSft6MSw", mapOf(
                                UserId("alice", "example.com") to keysOf(
                                    Key.Ed25519Key(
                                        "JLAFKJWSCS",
                                        "IQeCEPb9HFk217cU9kw9EOiusC6kMIkoIRnbnfOh5Oc63S1ghgyjShBGpu34blQomoalCyXWyhaaT3MrLZYQAA"
                                    )
                                )
                            )
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldQueryKeys() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getKeys)
            .whenInvokedWith(any())
            .then {
                GetKeys.Response(
                    failures = null,
                    deviceKeys = mapOf(
                        alice to mapOf(
                            "JLAFKJWSCS" to Signed(
                                signed = DeviceKeys(
                                    userId = alice,
                                    deviceId = "JLAFKJWSCS",
                                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                                    keys = keysOf(
                                        Key.Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                        Key.Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                                    )
                                ),
                                signatures = mapOf(
                                    alice to keysOf(
                                        Key.Ed25519Key(
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
                                usage = setOf(CrossSigningKeysUsage.MasterKey),
                                keys = keysOf(Key.Ed25519Key("base64+master+public+key", "base64+master+public+key"))
                            )
                        )
                    ),
                    selfSigningKeys = mapOf(
                        alice to Signed(
                            signed = CrossSigningKeys(
                                userId = alice,
                                usage = setOf(CrossSigningKeysUsage.SelfSigningKey),
                                keys = keysOf(
                                    Key.Ed25519Key(
                                        "base64+self+signing+public+key",
                                        "base64+self+signing+public+key"
                                    )
                                )
                            ),
                            signatures = mapOf(
                                alice to keysOf(
                                    Key.Ed25519Key(
                                        "base64+master+public+key",
                                        "signature+of+self+signing+key"
                                    )
                                )
                            )
                        )
                    ),
                    userSigningKeys = mapOf(
                        alice to Signed(
                            signed = CrossSigningKeys(
                                userId = alice,
                                usage = setOf(CrossSigningKeysUsage.UserSigningKey),
                                keys = keysOf(
                                    Key.Ed25519Key(
                                        "base64+user+signing+public+key",
                                        "base64+user+signing+public+key"
                                    )
                                )
                            ),
                            signatures = mapOf(
                                alice to keysOf(
                                    Key.Ed25519Key(
                                        "base64+master+public+key",
                                        "signature+of+user+signing+key"
                                    )
                                )
                            )
                        )
                    )
                )
            }
        val response = client.post("/_matrix/client/v3/keys/query") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_keys":{
                    "@alice:example.com":[]
                  },
                  "token":"string",
                  "timeout":10000
                }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "device_keys":{
                    "@alice:example.com":{
                      "JLAFKJWSCS":{
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
                      }
                    }
                  },
                  "master_keys":{
                    "@alice:example.com":{
                      "user_id":"@alice:example.com",
                      "usage":[
                        "master"
                      ],
                      "keys":{
                        "ed25519:base64+master+public+key":"base64+master+public+key"
                      }
                    }
                  },
                  "self_signing_keys":{
                    "@alice:example.com":{
                      "user_id":"@alice:example.com",
                      "usage":[
                        "self_signing"
                      ],
                      "keys":{
                        "ed25519:base64+self+signing+public+key":"base64+self+signing+public+key"
                      },
                      "signatures":{
                        "@alice:example.com":{
                          "ed25519:base64+master+public+key":"signature+of+self+signing+key"
                        }
                      }
                    }
                  },
                  "user_signing_keys":{
                    "@alice:example.com":{
                      "user_id":"@alice:example.com",
                      "usage":[
                        "user_signing"
                      ],
                      "keys":{
                        "ed25519:base64+user+signing+public+key":"base64+user+signing+public+key"
                      },
                      "signatures":{
                        "@alice:example.com":{
                          "ed25519:base64+master+public+key":"signature+of+user+signing+key"
                        }
                      }
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getKeys)
            .with(matching {
                it.requestBody shouldBe GetKeys.Request(
                    timeout = 10_000,
                    deviceKeys = mapOf(UserId("alice", "example.com") to setOf()),
                    token = "string"
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldClaimKeys() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::claimKeys)
            .whenInvokedWith(any())
            .then {
                ClaimKeys.Response(
                    failures = mapOf(),
                    oneTimeKeys = mapOf(
                        alice to mapOf(
                            "JLAFKJWSCS" to keysOf(
                                Key.SignedCurve25519Key(
                                    "AAAAHg",
                                    "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                    mapOf(
                                        alice to keysOf(
                                            Key.Ed25519Key(
                                                "JLAFKJWSCS",
                                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            }
        val response = client.post("/_matrix/client/v3/keys/claim") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "one_time_keys":{
                    "@alice:example.com":{
                      "JLAFKJWSCS":"signed_curve25519"
                    }
                  },
                  "timeout":10000
                }
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "failures":{},
                  "one_time_keys":{
                    "@alice:example.com":{
                      "JLAFKJWSCS":{
                        "signed_curve25519:AAAAHg":{
                          "key":"zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                          "signatures":{
                            "@alice:example.com":{
                              "ed25519:JLAFKJWSCS":"FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::claimKeys)
            .with(matching {
                it.requestBody shouldBe ClaimKeys.Request(
                    timeout = 10_000,
                    oneTimeKeys = mapOf(alice to mapOf("JLAFKJWSCS" to KeyAlgorithm.SignedCurve25519)),
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetKeyChanges() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getKeyChanges)
            .whenInvokedWith(any())
            .then {
                GetKeyChanges.Response(
                    changed = setOf(UserId("@alice:example.com"), UserId("@bob:example.org")),
                    left = setOf(UserId("@clara:example.com"), UserId("@doug:example.org"))
                )
            }
        val response = client.get("/_matrix/client/v3/keys/changes?from=s72594_4483_1934&to=s75689_5632_2435") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "changed":[
                    "@alice:example.com",
                    "@bob:example.org"
                  ],
                  "left":[
                    "@clara:example.com",
                    "@doug:example.org"
                  ]
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getKeyChanges)
            .with(matching {
                it.endpoint.from shouldBe "s72594_4483_1934"
                it.endpoint.to shouldBe "s75689_5632_2435"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetCrossSigningKeys() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setCrossSigningKeys)
            .whenInvokedWith(any())
            .then {
                ResponseWithUIA.Success(Unit)
            }
        val response = client.post("/_matrix/client/v3/keys/device_signing/upload") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
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
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setCrossSigningKeys)
            .with(matching {
                it.requestBody shouldBe RequestWithUIA(
                    SetCrossSigningKeys.Request(
                        masterKey = Signed(
                            CrossSigningKeys(
                                keys = keysOf(Key.Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                                usage = setOf(CrossSigningKeysUsage.MasterKey),
                                userId = UserId("@alice:example.com"),
                            ),
                            mapOf(
                                UserId("@alice:example.com") to keysOf(
                                    Key.Ed25519Key("alice+base64+master+key", "signature+of+key")
                                )
                            )
                        ),
                        selfSigningKey = Signed(
                            CrossSigningKeys(
                                keys = keysOf(Key.Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                                usage = setOf(CrossSigningKeysUsage.SelfSigningKey),
                                userId = UserId("@alice:example.com"),
                            ),
                            mapOf(
                                UserId("@alice:example.com") to keysOf(
                                    Key.Ed25519Key("alice+base64+master+key", "signature+of+key"),
                                    Key.Ed25519Key("base64+master+public+key", "signature+of+self+signing+key")
                                )
                            )
                        ),
                        userSigningKey = Signed(
                            CrossSigningKeys(
                                keys = keysOf(Key.Ed25519Key("alice+base64+public+key", "alice+base64+public+key")),
                                usage = setOf(CrossSigningKeysUsage.UserSigningKey),
                                userId = UserId("@alice:example.com"),
                            ),
                            mapOf(
                                UserId("@alice:example.com") to keysOf(
                                    Key.Ed25519Key("alice+base64+master+key", "signature+of+key"),
                                    Key.Ed25519Key("base64+master+public+key", "signature+of+user+signing+key")
                                )
                            )
                        )
                    ), null
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldAddSignatures() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::addSignatures)
            .whenInvokedWith(any())
            .then {
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
                )
            }
        val response = client.post("/_matrix/client/v3/keys/signatures/upload") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
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
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "failures":{
                    "@alice:example.com":{
                      "HIJKLMN":{
                        "errcode":"M_INVALID_SIGNATURE",
                        "error":"Invalid signature"
                      }
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::addSignatures)
            .with(matching {
                it.requestBody shouldBe mapOf(
                    UserId("@alice:example.com") to JsonObject(
                        mapOf(
                            "HIJKLMN" to JsonObject(
                                mapOf(
                                    "user_id" to JsonPrimitive("@alice:example.com"),
                                    "device_id" to JsonPrimitive("HIJKLMN"),
                                    "algorithms" to JsonArray(
                                        listOf(
                                            JsonPrimitive("m.olm.v1.curve25519-aes-sha2"),
                                            JsonPrimitive("m.megolm.v1.aes-sha2")
                                        )
                                    ),
                                    "keys" to JsonObject(
                                        mapOf(
                                            "ed25519:HIJKLMN" to JsonPrimitive("base64+ed25519+key"),
                                            "curve25519:HIJKLMN" to JsonPrimitive("base64+curve25519+key")
                                        )
                                    ),
                                    "signatures" to JsonObject(
                                        mapOf(
                                            "@alice:example.com" to JsonObject(
                                                mapOf(
                                                    "ed25519:base64+self+signing+public+key" to JsonPrimitive("base64+signature+of+HIJKLMN")
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            "base64+master+public+key" to JsonObject(
                                mapOf(
                                    "user_id" to JsonPrimitive("@alice:example.com"),
                                    "usage" to JsonArray(listOf(JsonPrimitive("master"))),
                                    "keys" to JsonObject(
                                        mapOf(
                                            "ed25519:base64+master+public+key" to JsonPrimitive("base64+master+public+key"),
                                        )
                                    ),
                                    "signatures" to JsonObject(
                                        mapOf(
                                            "@alice:example.com" to JsonObject(
                                                mapOf(
                                                    "ed25519:HIJKLMN" to JsonPrimitive("base64+signature+of+master+key")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    UserId("@bob:example.com") to JsonObject(
                        mapOf(
                            "bobs+base64+master+public+key" to JsonObject(
                                mapOf(
                                    "user_id" to JsonPrimitive("@bob:example.com"),
                                    "usage" to JsonArray(listOf(JsonPrimitive("master"))),
                                    "keys" to JsonObject(
                                        mapOf(
                                            "ed25519:bobs+base64+master+public+key" to JsonPrimitive("bobs+base64+master+public+key"),
                                        )
                                    ),
                                    "signatures" to JsonObject(
                                        mapOf(
                                            "@alice:example.com" to JsonObject(
                                                mapOf(
                                                    "ed25519:base64+user+signing+public+key" to JsonPrimitive("base64+signature+of+bobs+master+key")
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomsKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomsKeyBackup)
            .whenInvokedWith(any())
            .then {
                RoomsKeyBackup(
                    mapOf(
                        RoomId("!room:example.org") to RoomKeyBackup(
                            mapOf(
                                "+ess/ionId1" to RoomKeyBackupData(
                                    firstMessageIndex = 1,
                                    forwardedCount = 0,
                                    isVerified = true,
                                    sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                                        ciphertext = "base64+ciphertext+of+JSON+data",
                                        ephemeral = "base64+ephemeral+key",
                                        mac = "base64+mac+of+ciphertext"
                                    )
                                )
                            )
                        )
                    )
                )
            }
        val response = client.get("/_matrix/client/v3/room_keys/keys?version=1") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
        }
        verify(handlerMock).suspendFunction(handlerMock::getRoomsKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomKeyBackup)
            .whenInvokedWith(any())
            .then {
                RoomKeyBackup(
                    mapOf(
                        "+ess/ionId1" to RoomKeyBackupData(
                            firstMessageIndex = 1,
                            forwardedCount = 0,
                            isVerified = true,
                            sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                                ciphertext = "base64+ciphertext+of+JSON+data",
                                ephemeral = "base64+ephemeral+key",
                                mac = "base64+mac+of+ciphertext"
                            )
                        )
                    )
                )
            }
        val response =
            client.get("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg?version=1") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
        }
        verify(handlerMock).suspendFunction(handlerMock::getRoomKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomKeyBackupData() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupData)
            .whenInvokedWith(any())
            .then {
                RoomKeyBackupData(
                    firstMessageIndex = 1,
                    forwardedCount = 0,
                    isVerified = true,
                    sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                        ciphertext = "base64+ciphertext+of+JSON+data",
                        ephemeral = "base64+ephemeral+key",
                        mac = "base64+mac+of+ciphertext"
                    )
                )
            }
        val response =
            client.get("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg/%2Bess%2FionId1?version=1") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
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
        }
        verify(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupData)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                it.endpoint.sessionId shouldBe "+ess/ionId1"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomsKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setRoomsKeyBackup)
            .whenInvokedWith(any())
            .then {
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response = client.put("/_matrix/client/v3/room_keys/keys?version=1") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
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
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::setRoomsKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.requestBody shouldBe RoomsKeyBackup(
                    mapOf(
                        RoomId("!room:example.org") to RoomKeyBackup(
                            mapOf(
                                "+ess/ionId1" to RoomKeyBackupData(
                                    firstMessageIndex = 1,
                                    forwardedCount = 0,
                                    isVerified = true,
                                    sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                                        ciphertext = "base64+ciphertext+of+JSON+data",
                                        ephemeral = "base64+ephemeral+key",
                                        mac = "base64+mac+of+ciphertext"
                                    )
                                )
                            )
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setRoomKeyBackup)
            .whenInvokedWith(any())
            .then {
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response = client.put("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg?version=1") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
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
                """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::setRoomKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                it.requestBody shouldBe RoomKeyBackup(
                    mapOf(
                        "+ess/ionId1" to RoomKeyBackupData(
                            firstMessageIndex = 1,
                            forwardedCount = 0,
                            isVerified = true,
                            sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                                ciphertext = "base64+ciphertext+of+JSON+data",
                                ephemeral = "base64+ephemeral+key",
                                mac = "base64+mac+of+ciphertext"
                            )
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomKeyBackupData() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setRoomKeyBackupData)
            .whenInvokedWith(any())
            .then {
                SetRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response =
            client.put("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg/%2Bess%2FionId1?version=1") {
                bearerAuth("token")
                contentType(ContentType.Application.Json)
                setBody(
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
                """.trimIndent()
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::setRoomKeyBackupData)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                it.endpoint.sessionId shouldBe "+ess/ionId1"
                it.requestBody shouldBe RoomKeyBackupData(
                    firstMessageIndex = 1,
                    forwardedCount = 0,
                    isVerified = true,
                    sessionData = RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData(
                        ciphertext = "base64+ciphertext+of+JSON+data",
                        ephemeral = "base64+ephemeral+key",
                        mac = "base64+mac+of+ciphertext"
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteRoomsKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteRoomsKeyBackup)
            .whenInvokedWith(any())
            .then {
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response = client.delete("/_matrix/client/v3/room_keys/keys?version=1") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteRoomsKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteRoomKeyBackup() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteRoomKeyBackup)
            .whenInvokedWith(any())
            .then {
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response = client.delete("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg?version=1") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteRoomKeyBackup)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteRoomKeyBackupData() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::deleteRoomKeyBackupData)
            .whenInvokedWith(any())
            .then {
                DeleteRoomKeysResponse(count = 10, etag = "abcdefg")
            }
        val response =
            client.delete("/_matrix/client/v3/room_keys/keys/%21room%3Aexample%2Eorg/%2Bess%2FionId1?version=1") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "count":10,
                  "etag":"abcdefg"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteRoomKeyBackupData)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.endpoint.roomId shouldBe RoomId("!room:example.org")
                it.endpoint.sessionId shouldBe "+ess/ionId1"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomKeyBackupVersion() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupVersion)
            .whenInvokedWith(any())
            .then {
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Key.Curve25519Key(value = "abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Key.Ed25519Key("deviceid", "signature"))
                        )
                    ),
                    count = 42,
                    etag = "anopaquestring",
                    version = "1"
                )
            }
        val response = client.get("/_matrix/client/v3/room_keys/version") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "auth_data":{
                    "public_key":"abcdefg",
                    "signatures":{
                      "@alice:example.org":{
                        "ed25519:deviceid":"signature"
                      }
                    }
                  },
                  "count":42,
                  "etag":"anopaquestring",
                  "version":"1",
                  "algorithm":"m.megolm_backup.v1.curve25519-aes-sha2"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupVersion)
            .with(matching {
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldGetRoomKeyBackupVersionByVersion() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupVersionByVersion)
            .whenInvokedWith(any())
            .then {
                GetRoomKeysBackupVersionResponse.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Key.Curve25519Key(value = "abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Key.Ed25519Key("deviceid", "signature"))
                        )
                    ),
                    count = 42,
                    etag = "anopaquestring",
                    version = "1"
                )
            }
        val response = client.get("/_matrix/client/v3/room_keys/version/1") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "auth_data":{
                    "public_key":"abcdefg",
                    "signatures":{
                      "@alice:example.org":{
                        "ed25519:deviceid":"signature"
                      }
                    }
                  },
                  "count":42,
                  "etag":"anopaquestring",
                  "version":"1",
                  "algorithm":"m.megolm_backup.v1.curve25519-aes-sha2"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::getRoomKeyBackupVersionByVersion)
            .with(matching {
                it.endpoint.version shouldBe "1"
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomKeyBackupVersion() = testApplication {
        initCut()
        given(handlerMock).suspendFunction(handlerMock::setRoomKeyBackupVersion)
            .whenInvokedWith(any())
            .then {
                SetRoomKeyBackupVersion.Response("1")
            }
        val response = client.post("/_matrix/client/v3/room_keys/version") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
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
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "version":"1"
                }
            """.trimToFlatJson()
        }
        verify(handlerMock).suspendFunction(handlerMock::setRoomKeyBackupVersion)
            .with(matching {
                it.requestBody shouldBe SetRoomKeyBackupVersionRequest.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Key.Curve25519Key(value = "abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Key.Ed25519Key("deviceid", "signature"))
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldSetRoomKeyBackupVersionByVersion() = testApplication {
        initCut()
        val response = client.put("/_matrix/client/v3/room_keys/version/1") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
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
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::setRoomKeyBackupVersionByVersion)
            .with(matching {
                it.endpoint.version shouldBe "1"
                it.requestBody shouldBe SetRoomKeyBackupVersionRequest.V1(
                    authData = RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(
                        publicKey = Key.Curve25519Key(value = "abcdefg"),
                        signatures = mapOf(
                            UserId("@alice:example.org") to keysOf(Key.Ed25519Key("deviceid", "signature"))
                        )
                    )
                )
                true
            })
            .wasInvoked()
    }

    @Test
    fun shouldDeleteRoomKeyBackupVersionByVersion() = testApplication {
        initCut()
        val response = client.delete("/_matrix/client/v3/room_keys/version/1") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verify(handlerMock).suspendFunction(handlerMock::deleteRoomKeyBackupVersion)
            .with(matching {
                it.endpoint.version shouldBe "1"
                true
            })
            .wasInvoked()
    }
}
