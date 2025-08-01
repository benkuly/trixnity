package net.folivo.trixnity.serverserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceDataUnitContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.SelfSigningKey
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit
import net.folivo.trixnity.serverserverapi.model.federation.*
import net.folivo.trixnity.serverserverapi.model.federation.OnBindThirdPid.Request.ThirdPartyInvite
import net.folivo.trixnity.serverserverapi.model.federation.SendTransaction.Response.PDUProcessingResult
import net.folivo.trixnity.serverserverapi.model.federation.ThumbnailResizingMethod.SCALE
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FederationRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventAndDataUnitJson(TestRoomVersionStore("12"))
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<FederationApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixSignatureAuth(hostname = "") {
                authenticationFunction = { SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            install(ConvertMediaPlugin)
            matrixApiServer(json) {
                federationApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    private val pdu: SignedPersistentDataUnit<*> = Signed(
        PersistentDataUnit.PersistentDataUnitV12.PersistentMessageDataUnitV12(
            authEvents = listOf(),
            content = RoomMessageEventContent.TextBased.Text("hi"),
            depth = 12u,
            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
            originTimestamp = 1404838188000,
            prevEvents = listOf(),
            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
            sender = UserId("@alice:example.com"),
            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
        ),
        mapOf(
            "matrix.org" to keysOf(
                Ed25519Key(
                    "key",
                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                )
            )
        )
    )

    private val pduJson = """
        {
          "auth_events": [],
          "content": {
            "body": "hi",
            "msgtype": "m.text"
          },
          "depth": 12,
          "hashes": {
            "sha256": "thishashcoversallfieldsincasethisisredacted"
          },
          "origin_server_ts": 1404838188000,
          "prev_events": [],
          "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
          "sender": "@alice:example.com",
          "type": "m.room.message",
          "unsigned": {
            "age": 4612
          },
          "signatures": {
              "matrix.org": {
                "ed25519:key": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
              }
          }                      
        }
    """.trimToFlatJson()

    @Test
    fun shouldSendTransaction() = testApplication {
        initCut()
        everySuspend { handlerMock.sendTransaction(any()) }
            .returns(
                SendTransaction.Response(
                    mapOf(
                        EventId("$1failed_event:example.org") to PDUProcessingResult("You are not allowed to send a message to this room."),
                        EventId("$1successful_event:example.org") to PDUProcessingResult()
                    )
                )
            )
        val response = client.put("/_matrix/federation/v1/send/someTransactionId") {
            contentType(ContentType.Application.Json)
            someSignature()
            setBody(
                """
                {
                  "edus": [
                    {
                      "content": {
                        "key": "value",
                        "push": [
                          {
                            "last_active_ago": 5000,
                            "presence": "online",
                            "user_id": "@john:matrix.org"
                          }
                        ]
                      },
                      "edu_type": "m.presence"
                    }
                  ],
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "pdus": {
                    "$1failed_event:example.org": {
                      "error": "You are not allowed to send a message to this room."
                    },
                    "$1successful_event:example.org": {}
                  }
                }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.sendTransaction(assert {
                it.endpoint.txnId shouldBe "someTransactionId"
                it.requestBody shouldBe SendTransaction.Request(
                    edus = listOf(
                        EphemeralDataUnit(
                            PresenceDataUnitContent(
                                listOf(
                                    PresenceDataUnitContent.PresenceUpdate(
                                        userId = UserId("@john:matrix.org"),
                                        presence = Presence.ONLINE,
                                        lastActiveAgo = 5000
                                    )
                                )
                            )
                        )
                    ),
                    origin = "matrix.org",
                    originTimestamp = 1234567890,
                    pdus = listOf(pdu)
                )
            })
        }
    }

    @Test
    fun shouldGetEventAuthChain() = testApplication {
        initCut()
        everySuspend { handlerMock.getEventAuthChain(any()) }
            .returns(
                GetEventAuthChain.Response(
                    listOf(pdu)
                )
            )
        val response = client.get("/_matrix/federation/v1/event_auth/!room:server/$1event") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "auth_chain": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getEventAuthChain(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
            })
        }
    }

    @Test
    fun shouldBackfillRoom() = testApplication {
        initCut()
        everySuspend { handlerMock.backfillRoom(any()) }
            .returns(
                PduTransaction(
                    origin = "matrix.org",
                    originTimestamp = 1234567890,
                    pdus = listOf(pdu)
                )
            )
        val response = client.get("/_matrix/federation/v1/backfill/!room:server?v=$1event&limit=10") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.backfillRoom(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.startFrom shouldBe listOf(EventId("$1event"))
                it.endpoint.limit shouldBe 10
            })
        }
    }

    @Test
    fun shouldGetMissingEvents() = testApplication {
        initCut()
        everySuspend { handlerMock.getMissingEvents(any()) }
            .returns(
                PduTransaction(
                    origin = "matrix.org",
                    originTimestamp = 1234567890,
                    pdus = listOf(pdu)
                )
            )
        val response = client.post("/_matrix/federation/v1/get_missing_events/!room:server") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "earliest_events": [
                    "$1missing_event:example.org"
                  ],
                  "latest_events": [
                    "$1event_that_has_the_missing_event_as_a_previous_event:example.org"
                  ],
                  "limit": 10,
                  "min_depth": 0
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getMissingEvents(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe GetMissingEvents.Request(
                    earliestEvents = listOf(EventId("$1missing_event:example.org")),
                    latestEvents = listOf(EventId("$1event_that_has_the_missing_event_as_a_previous_event:example.org")),
                    limit = 10,
                    minDepth = 0,
                )
            })
        }
    }

    @Test
    fun shouldGetEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.getEvent(any()) }
            .returns(
                PduTransaction(
                    origin = "matrix.org",
                    originTimestamp = 1234567890,
                    pdus = listOf(pdu)
                )
            )
        val response = client.get("/_matrix/federation/v1/event/$1event") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getEvent(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
            })
        }
    }

    @Test
    fun shouldGetState() = testApplication {
        initCut()
        everySuspend { handlerMock.getState(any()) }
            .returns(
                GetState.Response(
                    authChain = listOf(pdu),
                    pdus = listOf(pdu)
                )
            )
        val response = client.get("/_matrix/federation/v1/state/!room:server?event_id=$1event") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "auth_chain": [$pduJson],
                      "pdus": [$pduJson]
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getState(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldGetStateIds() = testApplication {
        initCut()
        everySuspend { handlerMock.getStateIds(any()) }
            .returns(
                GetStateIds.Response(
                    authChainIds = listOf(EventId("$1event")),
                    pduIds = listOf(EventId("$2event"))
                )
            )
        val response = client.get("/_matrix/federation/v1/state_ids/!room:server?event_id=$1event") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "auth_chain_ids": ["$1event"],
                      "pdu_ids": ["$2event"]
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getStateIds(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldMakeJoin() = testApplication {
        initCut()
        everySuspend { handlerMock.makeJoin(any()) }
            .returns(
                MakeJoin.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                            membership = Membership.JOIN
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    roomVersion = "3"
                )
            )
        val response = client.get("/_matrix/federation/v1/make_join/!room:server/@alice:example.com?ver=3") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "join_authorised_via_users_server": "@anyone:resident.example.org",
                          "membership": "join"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "type": "m.room.member",
                        "unsigned":{"age":4612}
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.makeJoin(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.endpoint.supportedRoomVersions shouldBe setOf("3")
            })
        }
    }

    @Test
    fun shouldSendJoin() = testApplication {
        initCut()
        everySuspend { handlerMock.sendJoin(any()) }
            .returns(
                SendJoin.Response(
                    authChain = listOf(pdu),
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                                membership = Membership.JOIN
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Ed25519Key(
                                    "key_version",
                                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                )
                            ),
                            "resident.example.com" to keysOf(
                                Ed25519Key(
                                    "other_key_version",
                                    "a different signature"
                                )
                            )
                        )
                    ),
                    origin = "matrix.org",
                    state = listOf()
                )
            )
        val response = client.put("/_matrix/federation/v2/send_join/!room:server/$1event") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "auth_events":[],
                    "content": {
                      "membership": "join",
                      "join_authorised_via_users_server": "@anyone:resident.example.org"
                    },
                    "depth":12,
                    "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                    "origin_server_ts": 1404838188000,
                    "prev_events":[],
                    "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                    "sender": "@alice:example.com",
                    "state_key": "@alice:example.com",
                    "unsigned":{"age":4612},
                    "type": "m.room.member",
                    "signatures": {
                          "example.com": {
                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
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
                      "auth_chain":[$pduJson],
                      "event": {
                        "auth_events":[],
                        "content": {
                          "join_authorised_via_users_server": "@anyone:resident.example.org",
                          "membership": "join"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "type": "m.room.member",
                        "unsigned":{"age":4612},
                        "signatures": {
                          "example.com": {
                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                          },
                          "resident.example.com": {
                            "ed25519:other_key_version": "a different signature"
                          }
                        }
                      },
                      "origin": "matrix.org",
                      "state": []
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.sendJoin(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                            membership = Membership.JOIN
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Ed25519Key(
                                "key_version",
                                "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                            )
                        ),
                    )
                )
            })
        }
    }

    @Test
    fun shouldMakeKnock() = testApplication {
        initCut()
        everySuspend { handlerMock.makeKnock(any()) }
            .returns(
                MakeKnock.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.KNOCK
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    roomVersion = "3"
                )
            )
        val response = client.get("/_matrix/federation/v1/make_knock/!room:server/@alice:example.com?ver=3") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "knock"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "type": "m.room.member",
                        "unsigned":{"age":4612}
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.makeKnock(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
                it.endpoint.supportedRoomVersions shouldBe setOf("3")
            })
        }
    }

    @Test
    fun shouldSendKnock() = testApplication {
        initCut()
        everySuspend { handlerMock.sendKnock(any()) }
            .returns(
                SendKnock.Response(
                    listOf(
                        StrippedStateEvent(
                            content = NameEventContent("Example Room"),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        ),
                        StrippedStateEvent(
                            content = JoinRulesEventContent(JoinRulesEventContent.JoinRule.Knock),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        )
                    ),
                )
            )
        val response = client.put("/_matrix/federation/v1/send_knock/!room:server/$1event") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "auth_events":[],
                    "content": {
                      "membership": "knock"
                    },
                    "depth":12,
                    "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                    "origin_server_ts": 1404838188000,
                    "prev_events":[],
                    "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                    "sender": "@alice:example.com",
                    "state_key": "@alice:example.com",
                    "unsigned":{"age":4612},
                    "type": "m.room.member",
                    "signatures": {
                          "example.com": {
                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
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
                      "knock_room_state": [
                        {
                          "content": {
                            "name": "Example Room"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.name"
                        },
                        {
                          "content": {
                            "join_rule": "knock"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.join_rules"
                        }
                      ]
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.sendKnock(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.KNOCK
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Ed25519Key(
                                "key_version",
                                "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                            )
                        ),
                    )
                )
            })
        }
    }

    @Test
    fun shouldInvite() = testApplication {
        initCut()
        everySuspend { handlerMock.invite(any()) }
            .returns(
                Invite.Response(
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                membership = Membership.INVITE
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Ed25519Key(
                                    "key_version",
                                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                )
                            )
                        )
                    )
                )
            )
        val response = client.put("/_matrix/federation/v2/invite/!room:server/$1event") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "event": {
                    "auth_events":[],
                    "content": {
                      "membership": "invite"
                    },
                    "depth":12,
                    "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                    "origin_server_ts": 1404838188000,
                    "prev_events":[],
                    "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                    "sender": "@alice:example.com",
                    "state_key": "@alice:example.com",
                    "unsigned":{"age":4612},
                    "type": "m.room.member",
                    "signatures": {
                          "example.com": {
                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                          }
                    }
                  },
                  "invite_room_state": [
                        {
                          "content": {
                            "name": "Example Room"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.name"
                        },
                        {
                          "content": {
                            "join_rule": "invite"
                          },
                          "sender": "@bob:example.org",
                          "state_key": "",
                          "type": "m.room.join_rules"
                        }
                  ],
                  "room_version": "3"
                }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                            "auth_events":[],
                            "content": {
                              "membership": "invite"
                            },
                            "depth":12,
                            "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                            "origin_server_ts": 1404838188000,
                            "prev_events":[],
                            "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                            "sender": "@alice:example.com",
                            "state_key": "@alice:example.com",
                            "type": "m.room.member",
                            "unsigned":{"age":4612},
                            "signatures": {
                                  "example.com": {
                                    "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                  }
                            }
                          }
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.invite(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Invite.Request(
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                membership = Membership.INVITE
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Ed25519Key(
                                    "key_version",
                                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                )
                            ),
                        )
                    ),
                    inviteRoomState = listOf(
                        StrippedStateEvent(
                            content = NameEventContent("Example Room"),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        ),
                        StrippedStateEvent(
                            content = JoinRulesEventContent(JoinRulesEventContent.JoinRule.Invite),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        )
                    ),
                    roomVersion = "3"
                )
            })
        }
    }

    @Test
    fun shouldMakeLeave() = testApplication {
        initCut()
        everySuspend { handlerMock.makeLeave(any()) }
            .returns(
                MakeLeave.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.LEAVE
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    roomVersion = "3"
                )
            )
        val response = client.get("/_matrix/federation/v1/make_leave/!room:server/@alice:example.com") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "leave"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "type": "m.room.member",
                        "unsigned":{"age":4612}
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.makeLeave(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldSendLeave() = testApplication {
        initCut()
        everySuspend { handlerMock.sendLeave(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/federation/v2/send_leave/!room:server/$1event") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "auth_events":[],
                    "content": {
                      "membership": "leave"
                    },
                    "depth":12,
                    "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                    "origin_server_ts": 1404838188000,
                    "prev_events":[],
                    "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                    "sender": "@alice:example.com",
                    "state_key": "@alice:example.com",
                    "unsigned":{"age":4612},
                    "type": "m.room.member",
                    "signatures": {
                          "example.com": {
                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
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
        verifySuspend {
            handlerMock.sendLeave(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.LEAVE
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Ed25519Key(
                                "key_version",
                                "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                            )
                        ),
                    )
                )
            })
        }
    }

    @Test
    fun shouldOnBindThirdPid() = testApplication {
        initCut()
        everySuspend { handlerMock.onBindThirdPid(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/federation/v1/3pid/onbind") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "address": "alice@example.com",
                      "invites": [
                        {
                          "address": "alice@example.com",
                          "medium": "email",
                          "mxid": "@alice:matrix.org",
                          "room_id": "!somewhere:example.org",
                          "sender": "@bob:matrix.org",
                          "signed": {
                            "mxid": "@alice:matrix.org",
                            "signatures": {
                              "vector.im": {
                                "ed25519:0": "SomeSignatureGoesHere"
                              }
                            },
                            "token": "Hello World"
                          }
                        }
                      ],
                      "medium": "email",
                      "mxid": "@alice:matrix.org"
                    }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.onBindThirdPid(assert {
                it.requestBody shouldBe OnBindThirdPid.Request(
                    address = "alice@example.com",
                    invites = listOf(
                        ThirdPartyInvite(
                            address = "alice@example.com",
                            medium = "email",
                            userId = UserId("@alice:matrix.org"),
                            roomId = RoomId("!somewhere:example.org"),
                            sender = UserId("@bob:matrix.org"),
                            signed = Signed(
                                ThirdPartyInvite.UserInfo(UserId("@alice:matrix.org"), "Hello World"),
                                mapOf("vector.im" to keysOf(Ed25519Key("0", "SomeSignatureGoesHere")))
                            )
                        )
                    ),
                    medium = "email",
                    userId = UserId("@alice:matrix.org")
                )
            })
        }
    }

    @Test
    fun shouldExchangeThirdPartyInvite() = testApplication {
        initCut()
        everySuspend { handlerMock.exchangeThirdPartyInvite(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/federation/v1/exchange_third_party_invite/!room:server") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "auth_events":[],
                    "content": {
                        "membership": "invite",
                        "third_party_invite": {
                          "display_name": "alice",
                          "signed": {
                            "mxid": "@alice:localhost",
                            "signatures": {
                              "magic.forest": {
                                "ed25519:3": "fQpGIW1Snz+pwLZu6sTy2aHy/DYWWTspTJRPyNp0PKkymfIsNffysMl6ObMMFdIJhk6g6pwlIqZ54rxo8SLmAg"
                              }
                            },
                            "token": "abc123"
                          }
                        }
                    },
                    "depth":12,
                    "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                    "origin_server_ts": 1404838188000,
                    "prev_events":[],
                    "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                    "sender": "@alice:example.com",
                    "state_key": "@alice:example.com",
                    "unsigned":{"age":4612},
                    "type": "m.room.member"
                  }
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.exchangeThirdPartyInvite(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV12.PersistentStateDataUnitV12(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.INVITE,
                            thirdPartyInvite = MemberEventContent.Invite(
                                displayName = "alice",
                                signed = Signed(
                                    MemberEventContent.Invite.UserInfo(
                                        userId = UserId("@alice:localhost"),
                                        token = "abc123"
                                    ),
                                    mapOf(
                                        "magic.forest" to keysOf(
                                            Ed25519Key(
                                                "3",
                                                "fQpGIW1Snz+pwLZu6sTy2aHy/DYWWTspTJRPyNp0PKkymfIsNffysMl6ObMMFdIJhk6g6pwlIqZ54rxo8SLmAg"
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    )
                )
            })
        }
    }

    @Test
    fun shouldGetPublicRooms() = testApplication {
        initCut()
        everySuspend { handlerMock.getPublicRooms(any()) }
            .returns(
                GetPublicRoomsResponse(
                    chunk = listOf(
                        GetPublicRoomsResponse.PublicRoomsChunk(
                            avatarUrl = "mxc://bleecker.street/CHEDDARandBRIE",
                            guestCanJoin = false,
                            joinRule = JoinRulesEventContent.JoinRule.Public,
                            name = "CHEESE",
                            joinedMembersCount = 37,
                            roomId = RoomId("!ol19s:bleecker.street"),
                            topic = "Tasty tasty cheese",
                            worldReadable = true
                        )
                    ),
                    nextBatch = "p190q",
                    prevBatch = "p1902",
                    totalRoomCountEstimate = 115
                )
            )
        val response =
            client.get("/_matrix/federation/v1/publicRooms?limit=5&include_all_networks=false&since=since&third_party_instance_id=instance") {
                someSignature()
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "chunk": [
                    {
                      "avatar_url": "mxc://bleecker.street/CHEDDARandBRIE",
                      "guest_can_join": false,
                      "join_rule": "public",
                      "name": "CHEESE",
                      "num_joined_members": 37,
                      "room_id": "!ol19s:bleecker.street",
                      "topic": "Tasty tasty cheese",
                      "world_readable": true
                    }
                  ],
                  "next_batch": "p190q",
                  "prev_batch": "p1902",
                  "total_room_count_estimate": 115
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPublicRooms(assert {
                it.endpoint.limit shouldBe 5
                it.endpoint.includeAllNetworks shouldBe false
                it.endpoint.since shouldBe "since"
                it.endpoint.thirdPartyInstanceId shouldBe "instance"
            })
        }
    }

    @Test
    fun shouldGetPublicRoomsWithFilter() = testApplication {
        initCut()
        everySuspend { handlerMock.getPublicRoomsWithFilter(any()) }
            .returns(
                GetPublicRoomsResponse(
                    chunk = listOf(
                        GetPublicRoomsResponse.PublicRoomsChunk(
                            avatarUrl = "mxc://bleecker.street/CHEDDARandBRIE",
                            guestCanJoin = false,
                            joinRule = JoinRulesEventContent.JoinRule.Public,
                            name = "CHEESE",
                            joinedMembersCount = 37,
                            roomId = RoomId("!ol19s:bleecker.street"),
                            topic = "Tasty tasty cheese",
                            worldReadable = true
                        )
                    ),
                    nextBatch = "p190q",
                    prevBatch = "p1902",
                    totalRoomCountEstimate = 115
                )
            )
        val response =
            client.post("/_matrix/federation/v1/publicRooms") {
                someSignature()
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "filter": {
                        "generic_search_term": "foo"
                      },
                      "include_all_networks": false,
                      "limit": 10,
                      "third_party_instance_id": "irc"
                    }
                """.trimIndent()
                )
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "chunk": [
                    {
                      "avatar_url": "mxc://bleecker.street/CHEDDARandBRIE",
                      "guest_can_join": false,
                      "join_rule": "public",
                      "name": "CHEESE",
                      "num_joined_members": 37,
                      "room_id": "!ol19s:bleecker.street",
                      "topic": "Tasty tasty cheese",
                      "world_readable": true
                    }
                  ],
                  "next_batch": "p190q",
                  "prev_batch": "p1902",
                  "total_room_count_estimate": 115
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPublicRoomsWithFilter(assert {
                it.requestBody shouldBe GetPublicRoomsWithFilter.Request(
                    filter = GetPublicRoomsWithFilter.Request.Filter("foo"),
                    includeAllNetworks = false,
                    limit = 10,
                    thirdPartyInstanceId = "irc"
                )
            })
        }
    }

    @Test
    fun shouldGetHierarchy() = testApplication {
        initCut()
        everySuspend { handlerMock.getHierarchy(any()) }
            .returns(
                GetHierarchy.Response(
                    rooms = listOf(
                        GetHierarchy.Response.PublicRoomsChunk(
                            allowedRoomIds = setOf(RoomId("!upstream:example.org")),
                            avatarUrl = "mxc://example.org/abcdef2",
                            canonicalAlias = RoomAliasId("#general:example.org"),
                            childrenState = setOf(
                                StrippedStateEvent(
                                    ChildEventContent(via = setOf("remote.example.org")),
                                    originTimestamp = 1629422222222,
                                    sender = UserId("@alice:example.org"),
                                    stateKey = "!b:example.org",
                                )
                            ),
                            guestCanJoin = false,
                            joinRule = JoinRulesEventContent.JoinRule.Restricted,
                            name = "The ~~First~~ Second Space",
                            joinedMembersCount = 42,
                            roomId = RoomId("!second_room:example.org"),
                            roomType = CreateEventContent.RoomType.Space,
                            topic = "Hello world",
                            worldReadable = true
                        )
                    ),
                    inaccessible_children = setOf(RoomId("!secret:example.org")),
                    room = GetHierarchy.Response.PublicRoomsChunk(
                        allowedRoomIds = setOf(),
                        avatarUrl = "mxc://example.org/abcdef",
                        canonicalAlias = RoomAliasId("#general:example.org"),
                        childrenState = setOf(
                            StrippedStateEvent(
                                ChildEventContent(via = setOf("remote.example.org")),
                                originTimestamp = 1629413349153,
                                sender = UserId("@alice:example.org"),
                                stateKey = "!a:example.org",
                            )
                        ),
                        guestCanJoin = false,
                        joinRule = JoinRulesEventContent.JoinRule.Public,
                        name = "The First Space",
                        joinedMembersCount = 42,
                        roomId = RoomId("!space:example.org"),
                        roomType = CreateEventContent.RoomType.Space,
                        topic = "No other spaces were created first, ever",
                        worldReadable = true
                    )
                )
            )
        val response = client.get("/_matrix/federation/v1/hierarchy/!room:server?suggested_only=true") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "children": [
                    {
                      "allowed_room_ids": [
                        "!upstream:example.org"
                      ],
                      "avatar_url": "mxc://example.org/abcdef2",
                      "canonical_alias": "#general:example.org",
                      "children_state": [
                        {
                          "content": {
                            "suggested": false,
                            "via": [
                              "remote.example.org"
                            ]
                          },
                          "origin_server_ts": 1629422222222,
                          "sender": "@alice:example.org",
                          "state_key": "!b:example.org",
                          "type": "m.space.child"
                        }
                      ],
                      "guest_can_join": false,
                      "join_rule": "restricted",
                      "name": "The ~~First~~ Second Space",
                      "num_joined_members": 42,
                      "room_id": "!second_room:example.org",
                      "room_type": "m.space",
                      "topic": "Hello world",
                      "world_readable": true
                    }
                  ],
                  "inaccessible_children": [
                    "!secret:example.org"
                  ],
                  "room": {
                    "allowed_room_ids": [],
                    "avatar_url": "mxc://example.org/abcdef",
                    "canonical_alias": "#general:example.org",
                    "children_state": [
                      {
                        "content": {
                          "suggested": false,
                          "via": [
                            "remote.example.org"
                          ]
                        },
                        "origin_server_ts": 1629413349153,
                        "sender": "@alice:example.org",
                        "state_key": "!a:example.org",
                        "type": "m.space.child"
                      }
                    ],
                    "guest_can_join": false,
                    "join_rule": "public",
                    "name": "The First Space",
                    "num_joined_members": 42,
                    "room_id": "!space:example.org",
                    "room_type": "m.space",
                    "topic": "No other spaces were created first, ever",
                    "world_readable": true
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getHierarchy(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.suggestedOnly shouldBe true
            })
        }
    }

    @Test
    fun shouldQueryDirectory() = testApplication {
        initCut()
        everySuspend { handlerMock.queryDirectory(any()) }
            .returns(
                QueryDirectory.Response(
                    roomId = RoomId("!roomid1234:example.org"),
                    servers = setOf(
                        "example.org",
                        "example.com",
                        "another.example.com:8449"
                    )
                )
            )
        val response = client.get("/_matrix/federation/v1/query/directory?room_alias=%23alias:server") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "room_id": "!roomid1234:example.org",
                  "servers": [
                    "example.org",
                    "example.com",
                    "another.example.com:8449"
                  ]
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.queryDirectory(assert {
                it.endpoint.roomAlias shouldBe RoomAliasId("#alias:server")
            })
        }
    }

    @Test
    fun shouldQueryProfile() = testApplication {
        initCut()
        everySuspend { handlerMock.queryProfile(any()) }
            .returns(
                QueryProfile.Response(
                    displayname = "John Doe",
                    avatarUrl = "mxc://matrix.org/MyC00lAvatar"
                )
            )
        val response = client.get("/_matrix/federation/v1/query/profile?user_id=@user:server&field=displayname") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "avatar_url": "mxc://matrix.org/MyC00lAvatar",
                  "displayname": "John Doe"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.queryProfile(assert {
                it.endpoint.userId shouldBe UserId("@user:server")
                it.endpoint.field shouldBe QueryProfile.Field.DISPLAYNNAME
            })
        }
    }

    @Test
    fun shouldGetOIDCUserInfo() = testApplication {
        initCut()
        everySuspend { handlerMock.getOIDCUserInfo(any()) }
            .returns(
                GetOIDCUserInfo.Response(
                    sub = UserId("@alice:example.com")
                )
            )
        val response = client.get("/_matrix/federation/v1/openid/userinfo?access_token=token") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "sub": "@alice:example.com"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getOIDCUserInfo(assert {
                it.endpoint.accessToken shouldBe "token"
            })
        }
    }

    @Test
    fun shouldGetDevices() = testApplication {
        initCut()
        everySuspend { handlerMock.getDevices(any()) }
            .returns(
                GetDevices.Response(
                    devices = setOf(
                        GetDevices.Response.UserDevice(
                            deviceDisplayName = "Alice's Mobile Phone",
                            deviceId = "JLAFKJWSCS",
                            keys = Signed(
                                DeviceKeys(
                                    userId = UserId("@alice:example.com"),
                                    deviceId = "JLAFKJWSCS",
                                    algorithms = setOf(Olm, Megolm),
                                    keys = keysOf(
                                        Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                        Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                                    )
                                ),
                                mapOf(
                                    UserId("@alice:example.com") to keysOf(
                                        Ed25519Key(
                                            "JLAFKJWSCS",
                                            "dSO80A01XiigH3uBiDVx/EjzaoycHcjq9lfQX0uWsqxl2giMIiSPR8a4d291W1ihKJL/a+myXS367WT6NAIcBA"
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    masterKey = Signed(
                        CrossSigningKeys(
                            userId = UserId("@alice:example.com"),
                            usage = setOf(MasterKey),
                            keys = keysOf(
                                Ed25519Key("base64+master+public+key", "base64+master+public+key")
                            ),
                        ),
                        mapOf(
                            UserId("@alice:example.com") to keysOf(
                                Ed25519Key("alice+base64+master+key", "signature+of+key")
                            )
                        )
                    ),
                    selfSigningKey = Signed(
                        CrossSigningKeys(
                            userId = UserId("@alice:example.com"),
                            usage = setOf(SelfSigningKey),
                            keys = keysOf(
                                Ed25519Key("base64+self+signing+public+key", "base64+self+signing+public+key")
                            ),
                        ),
                        mapOf(
                            UserId("@alice:example.com") to keysOf(
                                Ed25519Key("alice+base64+master+key", "signature+of+key")
                            )
                        )
                    ),
                    streamId = 5,
                    userId = UserId("@alice:example.com")
                )
            )
        val response = client.get("/_matrix/federation/v1/user/devices/@alice:example.com") {
            someSignature()
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "devices": [
                    {
                      "device_display_name": "Alice's Mobile Phone",
                      "device_id": "JLAFKJWSCS",
                      "keys": {
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
                      }
                    }
                  ],
                  "master_key": {
                    "user_id": "@alice:example.com",
                    "usage": ["master"],
                    "keys": {
                      "ed25519:base64+master+public+key": "base64+master+public+key"
                    },
                    "signatures": {
                      "@alice:example.com": {
                        "ed25519:alice+base64+master+key": "signature+of+key"
                      }
                    }
                  },
                  "self_signing_key": {
                    "user_id": "@alice:example.com",
                    "usage": ["self_signing"],
                    "keys": {
                      "ed25519:base64+self+signing+public+key": "base64+self+signing+public+key"
                    },
                    "signatures": {
                      "@alice:example.com": {
                        "ed25519:alice+base64+master+key": "signature+of+key"
                      }
                    }
                  },
                  "stream_id": 5,
                  "user_id": "@alice:example.com"
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getDevices(assert {
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldClaimKeys() = testApplication {
        initCut()
        everySuspend { handlerMock.claimKeys(any()) }
            .returns(
                ClaimKeys.Response(
                    oneTimeKeys = mapOf(
                        UserId("@alice:example.com") to mapOf(
                            "JLAFKJWSCS" to keysOf(
                                Key.SignedCurve25519Key(
                                    id = "AAAAHg",
                                    value = "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                                    fallback = true,
                                    signatures = mapOf(
                                        UserId("@alice:example.com") to keysOf(
                                            Ed25519Key(
                                                "JLAFKJWSCS",
                                                "FLWxXqGbwrb8SM3Y795eB6OA8bwBcoMZFXBqnTn58AYWZSqiD45tlBVcDa2L7RwdKXebW/VzDlnfVJ+9jok1Bw"
                                            )
                                        )
                                    ),
                                )
                            )
                        )
                    )
                )
            )
        val response = client.post("/_matrix/federation/v1/user/keys/claim") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "one_time_keys":{
                    "@alice:example.com":{
                      "JLAFKJWSCS":"signed_curve25519"
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
                  "one_time_keys":{
                    "@alice:example.com":{
                      "JLAFKJWSCS":{
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
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.claimKeys(assert {
                it.requestBody shouldBe ClaimKeys.Request(
                    oneTimeKeys = mapOf(UserId("@alice:example.com") to mapOf("JLAFKJWSCS" to KeyAlgorithm.SignedCurve25519)),
                )
            })
        }
    }

    @Test
    fun shouldGetKeys() = testApplication {
        initCut()
        everySuspend { handlerMock.getKeys(any()) }
            .returns(
                GetKeys.Response(
                    deviceKeys = mapOf(
                        UserId("@alice:example.com") to mapOf(
                            "JLAFKJWSCS" to Signed(
                                signed = DeviceKeys(
                                    userId = UserId("@alice:example.com"),
                                    deviceId = "JLAFKJWSCS",
                                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                                    keys = keysOf(
                                        Key.Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                        Key.Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                                    )
                                ),
                                signatures = mapOf(
                                    UserId("@alice:example.com") to keysOf(
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
                        UserId("@alice:example.com") to Signed(
                            signed = CrossSigningKeys(
                                userId = UserId("@alice:example.com"),
                                usage = setOf(CrossSigningKeysUsage.MasterKey),
                                keys = keysOf(Key.Ed25519Key("base64+master+public+key", "base64+master+public+key"))
                            )
                        )
                    ),
                    selfSigningKeys = mapOf(
                        UserId("@alice:example.com") to Signed(
                            signed = CrossSigningKeys(
                                userId = UserId("@alice:example.com"),
                                usage = setOf(CrossSigningKeysUsage.SelfSigningKey),
                                keys = keysOf(
                                    Key.Ed25519Key(
                                        "base64+self+signing+public+key",
                                        "base64+self+signing+public+key"
                                    )
                                )
                            ),
                            signatures = mapOf(
                                UserId("@alice:example.com") to keysOf(
                                    Key.Ed25519Key(
                                        "base64+master+public+key",
                                        "signature+of+self+signing+key"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        val response = client.post("/_matrix/federation/v1/user/keys/query") {
            someSignature()
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_keys":{
                    "@alice:example.com":[]
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
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getKeys(assert {
                it.requestBody shouldBe GetKeys.Request(
                    keysFrom = mapOf(UserId("alice", "example.com") to setOf()),
                )
            })
        }
    }

    @Test
    fun shouldTimestampToEvent() = testApplication {
        initCut()
        everySuspend { handlerMock.timestampToEvent(any()) }
            .returns(
                TimestampToEvent.Response(
                    eventId = EventId("$143273582443PhrSn:example.org"),
                    originTimestamp = 1432735824653,
                )
            )
        val response =
            client.get("/_matrix/federation/v1/timestamp_to_event/!room:server?ts=24&dir=f") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "event_id": "$143273582443PhrSn:example.org",
                  "origin_server_ts": 1432735824653
               }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.timestampToEvent(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.timestamp shouldBe 24
                it.endpoint.dir shouldBe TimestampToEvent.Direction.FORWARDS
            })
        }
    }

    @Test
    fun downloadMediaStream() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadMedia(any()) }
            .returns(
                Media.Stream(
                    ByteReadChannel("a multiline\r\ntext file"),
                    22,
                    ContentType.Text.Plain,
                    ContentDisposition("attachment").withParameter("filename", "example.txt"),
                )
            )
        val response =
            client.get("/_matrix/federation/v1/media/download/mediaId123") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType()?.toString() shouldStartWith ContentType.MultiPart.Mixed.toString()
            val boundary = this.contentType()?.parameter("boundary")
            this.body<String>() shouldBe """
                --$boundary
                Content-Type: application/json
                
                {}
                --$boundary
                Content-Length: 22
                Content-Type: text/plain
                Content-Disposition: attachment; filename=example.txt
                
                a multiline
                text file
                --$boundary--
                
            """.trimIndent().replace("\n", "\r\n")
        }
        verifySuspend {
            handlerMock.downloadMedia(assert {
                it.endpoint.mediaId shouldBe "mediaId123"
            })
        }
    }

    @Test
    fun downloadMediaRedirect() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadMedia(any()) }
            .returns(
                Media.Redirect("https://example.org/mediablabla")
            )
        val response =
            client.get("/_matrix/federation/v1/media/download/mediaId123") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType()?.toString() shouldStartWith ContentType.MultiPart.Mixed.toString()
            val boundary = this.contentType()?.parameter("boundary")
            this.body<String>() shouldBe """
                --$boundary
                Content-Type: application/json
                
                {}
                --$boundary
                Location: https://example.org/mediablabla
                
                
                --$boundary--
                
            """.trimIndent().replace("\n", "\r\n")
        }
        verifySuspend {
            handlerMock.downloadMedia(assert {
                it.endpoint.mediaId shouldBe "mediaId123"
            })
        }
    }

    @Test
    fun downloadThumbnailStream() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadThumbnail(any()) }
            .returns(
                Media.Stream(
                    ByteReadChannel("a multiline\r\ntext file"),
                    22,
                    ContentType.Text.Plain,
                    ContentDisposition("attachment").withParameter("filename", "example.txt"),
                )
            )
        val response =
            client.get("/_matrix/federation/v1/media/thumbnail/mediaId123?width=64&height=64&method=scale") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType()?.toString() shouldStartWith ContentType.MultiPart.Mixed.toString()
            val boundary = this.contentType()?.parameter("boundary")
            this.body<String>() shouldBe """
                --$boundary
                Content-Type: application/json
                
                {}
                --$boundary
                Content-Length: 22
                Content-Type: text/plain
                Content-Disposition: attachment; filename=example.txt
                
                a multiline
                text file
                --$boundary--
                
            """.trimIndent().replace("\n", "\r\n")
        }
        verifySuspend {
            handlerMock.downloadThumbnail(assert {
                it.endpoint.mediaId shouldBe "mediaId123"
                it.endpoint.width shouldBe 64
                it.endpoint.height shouldBe 64
                it.endpoint.method shouldBe SCALE
            })
        }
    }

    @Test
    fun downloadThumbnailRedirect() = testApplication {
        initCut()
        everySuspend { handlerMock.downloadThumbnail(any()) }
            .returns(
                Media.Redirect("https://example.org/mediablabla")
            )
        val response =
            client.get("/_matrix/federation/v1/media/thumbnail/mediaId123?width=64&height=64&method=scale") {
                bearerAuth("token")
            }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType()?.toString() shouldStartWith ContentType.MultiPart.Mixed.toString()
            val boundary = this.contentType()?.parameter("boundary")
            this.body<String>() shouldBe """
                --$boundary
                Content-Type: application/json
                
                {}
                --$boundary
                Location: https://example.org/mediablabla
                
                
                --$boundary--
                
            """.trimIndent().replace("\n", "\r\n")
        }
        verifySuspend {
            handlerMock.downloadThumbnail(assert {
                it.endpoint.mediaId shouldBe "mediaId123"
                it.endpoint.width shouldBe 64
                it.endpoint.height shouldBe 64
                it.endpoint.method shouldBe SCALE
            })
        }
    }
}