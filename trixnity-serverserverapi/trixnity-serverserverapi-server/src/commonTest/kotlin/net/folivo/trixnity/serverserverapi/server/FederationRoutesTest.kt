package net.folivo.trixnity.serverserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceDataUnitContent
import net.folivo.trixnity.core.model.events.m.room.*
import net.folivo.trixnity.core.model.events.m.space.ChildEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit
import net.folivo.trixnity.serverserverapi.model.federation.*
import net.folivo.trixnity.serverserverapi.model.federation.OnBindThirdPid.Request.ThirdPartyInvite
import net.folivo.trixnity.serverserverapi.model.federation.SendTransaction.Response.PDUProcessingResult
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class FederationRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventAndDataUnitJson({ "3" })
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: FederationApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixSignatureAuth(hostname = "") {
                authenticationFunction = { SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    federationApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    private val pdu: SignedPersistentDataUnit<*> = Signed(
        PersistentDataUnit.PersistentDataUnitV3.PersistentMessageDataUnitV3(
            authEvents = listOf(),
            content = RoomMessageEventContent.TextMessageEventContent("hi"),
            depth = 12u,
            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
            origin = "example.com",
            originTimestamp = 1404838188000,
            prevEvents = listOf(),
            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
            sender = UserId("@alice:example.com"),
            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
        ),
        mapOf(
            "matrix.org" to keysOf(
                Key.Ed25519Key(
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
          "origin": "example.com",
          "origin_server_ts": 1404838188000,
          "prev_events": [],
          "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
          "sender": "@alice:example.com",
          "unsigned": {
            "age": 4612
          },
          "type": "m.room.message",
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
        everySuspending { handlerMock.sendTransaction(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.getEventAuthChain(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "auth_chain": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getEventAuthChain(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
            })
        }
    }

    @Test
    fun shouldBackfillRoom() = testApplication {
        initCut()
        everySuspending { handlerMock.backfillRoom(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.getMissingEvents(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.getEvent(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                {
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [$pduJson]
                }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getEvent(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
            })
        }
    }

    @Test
    fun shouldGetState() = testApplication {
        initCut()
        everySuspending { handlerMock.getState(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "auth_chain": [$pduJson],
                      "pdus": [$pduJson]
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getState(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldGetStateIds() = testApplication {
        initCut()
        everySuspending { handlerMock.getStateIds(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "auth_chain_ids": ["$1event"],
                      "pdu_ids": ["$2event"]
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getStateIds(assert {
                it.endpoint.eventId shouldBe EventId("$1event")
                it.endpoint.roomId shouldBe RoomId("!room:server")
            })
        }
    }

    @Test
    fun shouldMakeJoin() = testApplication {
        initCut()
        everySuspending { handlerMock.makeJoin(isAny()) }
            .returns(
                MakeJoin.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                            membership = Membership.JOIN
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "join",
                          "join_authorised_via_users_server": "@anyone:resident.example.org"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin": "example.com",
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "unsigned":{"age":4612},
                        "type": "m.room.member"
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.sendJoin(isAny()) }
            .returns(
                SendJoin.Response(
                    authChain = listOf(pdu),
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                                membership = Membership.JOIN
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            origin = "example.com",
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Key.Ed25519Key(
                                    "key_version",
                                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                )
                            ),
                            "resident.example.com" to keysOf(
                                Key.Ed25519Key(
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
                    "origin": "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "auth_chain":[$pduJson],
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "join",
                          "join_authorised_via_users_server": "@anyone:resident.example.org"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin": "example.com",
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
        verifyWithSuspend {
            handlerMock.sendJoin(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            joinAuthorisedViaUsersServer = UserId("@anyone:resident.example.org"),
                            membership = Membership.JOIN
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Key.Ed25519Key(
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
        everySuspending { handlerMock.makeKnock(isAny()) }
            .returns(
                MakeKnock.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.KNOCK
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "knock"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin": "example.com",
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "unsigned":{"age":4612},
                        "type": "m.room.member"
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.sendKnock(isAny()) }
            .returns(
                SendKnock.Response(
                    listOf(
                        Event.StrippedStateEvent(
                            content = NameEventContent("Example Room"),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        ),
                        Event.StrippedStateEvent(
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
                    "origin": "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
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
        verifyWithSuspend {
            handlerMock.sendKnock(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.KNOCK
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Key.Ed25519Key(
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
        everySuspending { handlerMock.invite(isAny()) }
            .returns(
                Invite.Response(
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                membership = Membership.INVITE
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            origin = "example.com",
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Key.Ed25519Key(
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
                    "origin": "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                            "auth_events":[],
                            "content": {
                              "membership": "invite"
                            },
                            "depth":12,
                            "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                            "origin": "example.com",
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
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.invite(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Invite.Request(
                    event = Signed(
                        PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                            authEvents = listOf(),
                            content = MemberEventContent(
                                membership = Membership.INVITE
                            ),
                            depth = 12u,
                            hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                            origin = "example.com",
                            originTimestamp = 1404838188000,
                            prevEvents = listOf(),
                            roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                            sender = UserId("@alice:example.com"),
                            stateKey = "@alice:example.com",
                            unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                        ),
                        mapOf(
                            "example.com" to keysOf(
                                Key.Ed25519Key(
                                    "key_version",
                                    "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                )
                            ),
                        )
                    ),
                    inviteRoomState = listOf(
                        Event.StrippedStateEvent(
                            content = NameEventContent("Example Room"),
                            sender = UserId("@bob:example.org"),
                            stateKey = ""
                        ),
                        Event.StrippedStateEvent(
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
        everySuspending { handlerMock.makeLeave(isAny()) }
            .returns(
                MakeLeave.Response(
                    eventTemplate = PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.LEAVE
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe """
                    {
                      "event": {
                        "auth_events":[],
                        "content": {
                          "membership": "leave"
                        },
                        "depth":12,
                        "hashes":{"sha256":"thishashcoversallfieldsincasethisisredacted"},
                        "origin": "example.com",
                        "origin_server_ts": 1404838188000,
                        "prev_events":[],
                        "room_id": "!UcYsUzyxTGDxLBEvLy:example.org",
                        "sender": "@alice:example.com",
                        "state_key": "@alice:example.com",
                        "unsigned":{"age":4612},
                        "type": "m.room.member"
                      },
                      "room_version": "3"
                    }
                """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.makeLeave(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.userId shouldBe UserId("@alice:example.com")
            })
        }
    }

    @Test
    fun shouldSendLeave() = testApplication {
        initCut()
        everySuspending { handlerMock.sendLeave(isAny()) }
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
                    "origin": "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.sendLeave(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.endpoint.eventId shouldBe EventId("$1event")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
                        authEvents = listOf(),
                        content = MemberEventContent(
                            membership = Membership.LEAVE
                        ),
                        depth = 12u,
                        hashes = PersistentDataUnit.EventHash("thishashcoversallfieldsincasethisisredacted"),
                        origin = "example.com",
                        originTimestamp = 1404838188000,
                        prevEvents = listOf(),
                        roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                        sender = UserId("@alice:example.com"),
                        stateKey = "@alice:example.com",
                        unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                    ),
                    mapOf(
                        "example.com" to keysOf(
                            Key.Ed25519Key(
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
        everySuspending { handlerMock.onBindThirdPid(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
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
                                mapOf("vector.im" to keysOf(Key.Ed25519Key("0", "SomeSignatureGoesHere")))
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
        everySuspending { handlerMock.exchangeThirdPartyInvite(isAny()) }
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
                    "origin": "example.com",
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
            handlerMock.exchangeThirdPartyInvite(assert {
                it.endpoint.roomId shouldBe RoomId("!room:server")
                it.requestBody shouldBe Signed(
                    PersistentDataUnit.PersistentDataUnitV3.PersistentStateDataUnitV3(
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
                                            Key.Ed25519Key(
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
                        origin = "example.com",
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
        everySuspending { handlerMock.getPublicRooms(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.getPublicRoomsWithFilter(isAny()) }
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
            this.contentType() shouldBe ContentType.Application.Json.withCharset(UTF_8)
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.getHierarchy(isAny()) }
            .returns(
                GetHierarchy.Response(
                    rooms = listOf(
                        GetHierarchy.Response.PublicRoomsChunk(
                            allowedRoomIds = setOf(RoomId("!upstream:example.org")),
                            avatarUrl = "mxc://example.org/abcdef2",
                            canonicalAlias = RoomAliasId("#general:example.org"),
                            childrenState = setOf(
                                Event.StrippedStateEvent(
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
                            Event.StrippedStateEvent(
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
                          "sender": "@alice:example.org",
                          "origin_server_ts": 1629422222222,
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
                        "sender": "@alice:example.org",
                        "origin_server_ts": 1629413349153,
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
        verifyWithSuspend {
            handlerMock.getHierarchy(assert {
                it.endpoint.roomId shouldBe RoomId("room", "server")
                it.endpoint.suggestedOnly shouldBe true
            })
        }
    }
}