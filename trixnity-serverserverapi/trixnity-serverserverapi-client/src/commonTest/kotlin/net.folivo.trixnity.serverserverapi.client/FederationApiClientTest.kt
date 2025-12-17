package net.folivo.trixnity.serverserverapi.client

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
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
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit
import net.folivo.trixnity.serverserverapi.model.federation.*
import net.folivo.trixnity.serverserverapi.model.federation.OnBindThirdPid.Request.ThirdPartyInvite
import net.folivo.trixnity.serverserverapi.model.federation.SendTransaction.Response.PDUProcessingResult
import net.folivo.trixnity.serverserverapi.model.federation.ThumbnailResizingMethod.SCALE
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class FederationApiClientTest : TrixnityBaseTest() {
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
    fun shouldSendTransaction() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/send/someTransactionId", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """
                        {
                          "edus": [
                            {
                              "content": {
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
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "pdus": {
                                "${'$'}1failed_event:example.org": {
                                  "error": "You are not allowed to send a message to this room."
                                },
                                "${'$'}1successful_event:example.org": {}
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.sendTransaction(
            Url(""),
            "someTransactionId",
            SendTransaction.Request(
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
        ).getOrThrow() shouldBe SendTransaction.Response(
            mapOf(
                EventId("$1failed_event:example.org") to PDUProcessingResult("You are not allowed to send a message to this room."),
                EventId("$1successful_event:example.org") to PDUProcessingResult()
            )
        )
    }

    @Test
    fun shouldGetEventAuthChain() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/event_auth/!room:server/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {  
                              "auth_chain": [$pduJson]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getEventAuthChain(Url(""), RoomId("!room:server"), EventId("$1event"))
            .getOrThrow() shouldBe GetEventAuthChain.Response(
            listOf(pdu)
        )
    }

    @Test
    fun shouldBackfillRoom() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/backfill/!room:server?v=%241event&limit=10",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "origin": "matrix.org",
                              "origin_server_ts": 1234567890,
                              "pdus": [$pduJson]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.backfillRoom(Url(""), RoomId("!room:server"), listOf(EventId("$1event")), 10)
            .getOrThrow() shouldBe PduTransaction(
            origin = "matrix.org",
            originTimestamp = 1234567890,
            pdus = listOf(pdu)
        )
    }

    @Test
    fun shouldGetMissingEvents() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/get_missing_events/!room:server", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                            {
                              "earliest_events": [
                                "${'$'}1missing_event:example.org"
                              ],
                              "latest_events": [
                                "${'$'}1event_that_has_the_missing_event_as_a_previous_event:example.org"
                              ],
                              "limit": 10,
                              "min_depth": 0
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
                            {
                              "origin": "matrix.org",
                              "origin_server_ts": 1234567890,
                              "pdus": [$pduJson]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getMissingEvents(
            Url(""),
            RoomId("!room:server"),
            GetMissingEvents.Request(
                earliestEvents = listOf(EventId("$1missing_event:example.org")),
                latestEvents = listOf(EventId("$1event_that_has_the_missing_event_as_a_previous_event:example.org")),
                limit = 10,
                minDepth = 0,
            )
        ).getOrThrow() shouldBe PduTransaction(
            origin = "matrix.org",
            originTimestamp = 1234567890,
            pdus = listOf(pdu)
        )
    }

    @Test
    fun shouldGetEvent() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/event/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "origin": "matrix.org",
                              "origin_server_ts": 1234567890,
                              "pdus": [$pduJson]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getEvent(Url(""), EventId("$1event"))
            .getOrThrow() shouldBe PduTransaction(
            origin = "matrix.org",
            originTimestamp = 1234567890,
            pdus = listOf(pdu)
        )
    }

    @Test
    fun shouldGetState() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/state/!room:server?event_id=%241event",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "auth_chain": [$pduJson],
                              "pdus": [$pduJson]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getState(Url(""), RoomId("!room:server"), EventId("$1event"))
            .getOrThrow() shouldBe GetState.Response(
            authChain = listOf(pdu),
            pdus = listOf(pdu)
        )
    }

    @Test
    fun shouldGetStateIds() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/state_ids/!room:server?event_id=%241event",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "auth_chain_ids": ["${'$'}1event"],
                              "pdu_ids": ["${'$'}2event"]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getStateIds(Url(""), RoomId("!room:server"), EventId("$1event"))
            .getOrThrow() shouldBe GetStateIds.Response(
            authChainIds = listOf(EventId("$1event")),
            pduIds = listOf(EventId("$2event"))
        )
    }

    @Test
    fun shouldMakeJoin() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/make_join/!room:server/@alice:example.com?ver=3",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.makeJoin(Url(""), RoomId("!room:server"), UserId("@alice:example.com"), setOf("3"))
            .getOrThrow() shouldBe MakeJoin.Response(
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
    }

    @Test
    fun shouldSendJoin() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v2/send_join/!room:server/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
                        """
                            {
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
                                      }
                                }
                              }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                              "state": []
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.sendJoin(
            Url(""),
            RoomId("!room:server"),
            EventId("$1event"),
            Signed(
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
                        Key.Ed25519Key(
                            "key_version",
                            "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                        )
                    ),
                )
            )
        ).getOrThrow() shouldBe SendJoin.Response(
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
            state = listOf()
        )
    }

    @Test
    fun shouldMakeKnock() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/make_knock/!room:server/@alice:example.com?ver=3",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.makeKnock(Url(""), RoomId("!room:server"), UserId("@alice:example.com"), setOf("3"))
            .getOrThrow() shouldBe MakeKnock.Response(
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
    }

    @Test
    fun shouldSendKnock() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/send_knock/!room:server/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
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
                                "type": "m.room.member",
                                "unsigned":{"age":4612},
                                "signatures": {
                                      "example.com": {
                                        "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                      }
                                }
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.sendKnock(
            Url(""),
            RoomId("!room:server"),
            EventId("$1event"),
            Signed(
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
                        Key.Ed25519Key(
                            "key_version",
                            "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                        )
                    ),
                )
            )
        ).getOrThrow() shouldBe SendKnock.Response(
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
    }

    @Test
    fun shouldInvite() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v2/invite/!room:server/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
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
                                "type": "m.room.member",
                                "unsigned":{"age":4612},
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
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
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
                                    "type": "m.room.member",
                                    "unsigned":{"age":4612},
                                    "signatures": {
                                          "example.com": {
                                            "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                          }
                                    }
                                  }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.invite(
            Url(""),
            RoomId("!room:server"),
            EventId("$1event"),
            Invite.Request(
                Signed(
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
                            Key.Ed25519Key(
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
        ).getOrThrow() shouldBe Invite.Response(
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
                        Key.Ed25519Key(
                            "key_version",
                            "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                        )
                    )
                )
            )
        )
    }

    @Test
    fun shouldMakeLeave() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/make_leave/!room:server/@alice:example.com",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.makeLeave(Url(""), RoomId("!room:server"), UserId("@alice:example.com"))
            .getOrThrow() shouldBe MakeLeave.Response(
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
    }

    @Test
    fun shouldSendLeave() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v2/send_leave/!room:server/$1event", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
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
                                "type": "m.room.member",
                                "unsigned":{"age":4612},
                                "signatures": {
                                      "example.com": {
                                        "ed25519:key_version": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                                      }
                                }
                              }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.sendLeave(
            Url(""),
            RoomId("!room:server"),
            EventId("$1event"),
            Signed(
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
                        Key.Ed25519Key(
                            "key_version",
                            "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                        )
                    ),
                )
            )
        ).getOrThrow() shouldBe Unit
    }

    @Test
    fun shouldOnBindThirdPid() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/federation/v1/3pid/onbind", request.url.fullPath)
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
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
                                    "token": "Hello World",
                                    "signatures": {
                                      "vector.im": {
                                        "ed25519:0": "SomeSignatureGoesHere"
                                      }
                                    }
                                  }
                                }
                              ],
                              "medium": "email",
                              "mxid": "@alice:matrix.org"
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.onBindThirdPid(
            Url(""),
            OnBindThirdPid.Request(
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
        ).getOrThrow() shouldBe Unit
    }

    @Test
    fun shouldExchangeThirdPartyInvite() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/exchange_third_party_invite/!room:server",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(
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
                                "type": "m.room.member",
                                "unsigned":{"age":4612}
                              }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.exchangeThirdPartyInvite(
            Url(""),
            RoomId("!room:server"),
            Signed(
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
                    originTimestamp = 1404838188000,
                    prevEvents = listOf(),
                    roomId = RoomId("!UcYsUzyxTGDxLBEvLy:example.org"),
                    sender = UserId("@alice:example.com"),
                    stateKey = "@alice:example.com",
                    unsigned = PersistentDataUnit.UnsignedData(age = 4612)
                )
            )
        ).getOrThrow() shouldBe Unit
    }

    @Test
    fun shouldGetPublicRooms() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/publicRooms?include_all_networks=false&limit=5&since=since&third_party_instance_id=instance",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getPublicRooms(
            Url(""),
            limit = 5,
            includeAllNetworks = false,
            since = "since",
            thirdPartyInstanceId = "instance"
        )
            .getOrThrow() shouldBe GetPublicRoomsResponse(
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
    }

    @Test
    fun shouldGetPublicRoomsWithFilter() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/publicRooms",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                            {
                              "filter": {
                                "generic_search_term": "foo"
                              },
                              "include_all_networks": false,
                              "limit": 10,
                              "third_party_instance_id": "irc"
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getPublicRoomsWithFilter(
            Url(""),
            GetPublicRoomsWithFilter.Request(
                filter = GetPublicRoomsWithFilter.Request.Filter("foo"),
                includeAllNetworks = false,
                limit = 10,
                thirdPartyInstanceId = "irc"
            )
        ).getOrThrow() shouldBe GetPublicRoomsResponse(
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
    }

    @Test
    fun shouldGetHierarchy() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/hierarchy/!room:server?suggested_only=true",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getHierarchy(
            Url(""),
            RoomId("!room:server"), true
        ).getOrThrow() shouldBe GetHierarchy.Response(
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
    }

    @Test
    fun shouldQueryDirectory() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/query/directory?room_alias=%23alias%3Aserver",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "room_id": "!roomid1234:example.org",
                              "servers": [
                                "example.org",
                                "example.com",
                                "another.example.com:8449"
                              ]
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.queryDirectory(
            Url(""),
            RoomAliasId("#alias:server")
        ).getOrThrow() shouldBe QueryDirectory.Response(
            roomId = RoomId("!roomid1234:example.org"),
            servers = setOf(
                "example.org",
                "example.com",
                "another.example.com:8449"
            )
        )
    }

    @Test
    fun shouldQueryProfile() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/query/profile?user_id=%40user%3Aserver&field=displayname",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "avatar_url": "mxc://matrix.org/MyC00lAvatar",
                              "displayname": "John Doe"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.queryProfile(
            Url(""),
            UserId("@user:server"), QueryProfile.Field.DISPLAYNNAME
        ).getOrThrow() shouldBe QueryProfile.Response(
            displayname = "John Doe",
            avatarUrl = "mxc://matrix.org/MyC00lAvatar"
        )
    }

    @Test
    fun shouldGetOIDCUserInfo() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/openid/userinfo?access_token=token",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            {
                              "sub": "@alice:example.com"
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getOIDCUserInfo(
            Url(""), "token"
        ).getOrThrow() shouldBe GetOIDCUserInfo.Response(
            sub = UserId("@alice:example.com")
        )
    }

    @Test
    fun shouldGetDevices() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/user/devices/@alice:example.com",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getDevices(
            Url(""), UserId("@alice:example.com")
        ).getOrThrow() shouldBe GetDevices.Response(
            devices = setOf(
                GetDevices.Response.UserDevice(
                    deviceDisplayName = "Alice's Mobile Phone",
                    deviceId = "JLAFKJWSCS",
                    keys = Signed(
                        DeviceKeys(
                            userId = UserId("@alice:example.com"),
                            deviceId = "JLAFKJWSCS",
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = keysOf(
                                Key.Curve25519Key("JLAFKJWSCS", "3C5BFWi2Y8MaVvjM8M22DBmh24PmgR0nPvJOIArzgyI"),
                                Key.Ed25519Key("JLAFKJWSCS", "lEuiRJBit0IG6nUf5pUzWTUEsRVVe/HJkoKuEww9ULI")
                            )
                        ),
                        mapOf(
                            UserId("@alice:example.com") to keysOf(
                                Key.Ed25519Key(
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
                    usage = setOf(CrossSigningKeysUsage.MasterKey),
                    keys = keysOf(
                        Key.Ed25519Key("base64+master+public+key", "base64+master+public+key")
                    ),
                ),
                mapOf(
                    UserId("@alice:example.com") to keysOf(
                        Key.Ed25519Key("alice+base64+master+key", "signature+of+key")
                    )
                )
            ),
            selfSigningKey = Signed(
                CrossSigningKeys(
                    userId = UserId("@alice:example.com"),
                    usage = setOf(CrossSigningKeysUsage.SelfSigningKey),
                    keys = keysOf(
                        Key.Ed25519Key("base64+self+signing+public+key", "base64+self+signing+public+key")
                    ),
                ),
                mapOf(
                    UserId("@alice:example.com") to keysOf(
                        Key.Ed25519Key("alice+base64+master+key", "signature+of+key")
                    )
                )
            ),
            streamId = 5,
            userId = UserId("@alice:example.com")
        )
    }

    @Test
    fun shouldClaimKeys() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/user/keys/claim",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                            {
                              "one_time_keys":{
                                "@alice:example.com":{
                                  "JLAFKJWSCS":"signed_curve25519"
                                }
                              }
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.claimKeys(
            Url(""),
            ClaimKeys.Request(
                oneTimeKeys = mapOf(UserId("@alice:example.com") to mapOf("JLAFKJWSCS" to KeyAlgorithm.SignedCurve25519)),
            )
        ).getOrThrow() shouldBe ClaimKeys.Response(
            oneTimeKeys = mapOf(
                UserId("@alice:example.com") to mapOf(
                    "JLAFKJWSCS" to keysOf(
                        Key.SignedCurve25519Key(
                            id = "AAAAHg",
                            value = "zKbLg+NrIjpnagy+pIY6uPL4ZwEG2v+8F9lmgsnlZzs",
                            fallback = true,
                            signatures = mapOf(
                                UserId("@alice:example.com") to keysOf(
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

    @Test
    fun shouldGetKeys() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/user/keys/query",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        """
                            {
                              "device_keys":{
                                "@alice:example.com":[]
                              }
                            }
                    """.trimToFlatJson(), request.body.toByteArray().decodeToString()
                    )
                    respond(
                        """
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
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.getKeys(
            Url(""),
            GetKeys.Request(
                keysFrom = mapOf(UserId("alice", "example.com") to setOf()),
            )
        ).getOrThrow() shouldBe GetKeys.Response(
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
    }

    @Test
    fun shouldTimestampToEvent() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/timestamp_to_event/!room:server?ts=24&dir=f",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                           {
                              "event_id": "${'$'}143273582443PhrSn:example.org",
                              "origin_server_ts": 1432735824653
                           }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.federation.timestampToEvent(
            roomId = RoomId("!room:server"),
            timestamp = 24,
            dir = TimestampToEvent.Direction.FORWARDS,
        ).getOrThrow() shouldBe TimestampToEvent.Response(
            eventId = EventId("$143273582443PhrSn:example.org"),
            originTimestamp = 1432735824653,
        )
    }

    @Test
    fun shouldDownloadMediaStream() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/media/download/mediaId123",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            --boundary
                            Content-Type: application/json

                            {}
                            --boundary
                            Content-Length: 22
                            Content-Type: text/plain
                            Content-Disposition: attachment; filename=example.txt

                            a multiline
                            text file
                            --boundary--

                        """.trimIndent().replace("\n", "\r\n"),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType,
                            ContentType.MultiPart.Mixed.withParameter("boundary", "boundary").toString()
                        )
                    )
                }
            })
        matrixRestClient.federation.downloadMedia("mediaId123") { media ->
            media.shouldBeInstanceOf<Media.Stream>()
            media.contentLength shouldBe 22
            media.contentType shouldBe ContentType.Text.Plain
            media.contentDisposition shouldBe ContentDisposition("attachment").withParameter("filename", "example.txt")
            media.content.toByteArray().decodeToString() shouldBe "a multiline\r\ntext file"
        }.getOrThrow()
    }

    @Test
    fun shouldDownloadMediaRedirect() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/media/download/mediaId123",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            --boundary
                            Content-Type: application/json

                            {}
                            --boundary
                            Location: https://example.org/mediablabla
                            
                            --boundary--

                        """.trimIndent().replace("\n", "\r\n"),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType,
                            ContentType.MultiPart.Mixed.withParameter("boundary", "boundary").toString()
                        )
                    )
                }
            })
        matrixRestClient.federation.downloadMedia("mediaId123") { media ->
            media.shouldBeInstanceOf<Media.Redirect>()
            media.location shouldBe "https://example.org/mediablabla"
        }.getOrThrow()
    }

    @Test
    fun shouldDownloadThumbnailStream() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/media/thumbnail/mediaId123?width=64&height=64&method=scale",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            --boundary
                            Content-Type: application/json

                            {}
                            --boundary
                            Content-Length: 22
                            Content-Type: text/plain
                            Content-Disposition: attachment; filename=example.txt

                            a multiline
                            text file
                            --boundary--

                        """.trimIndent().replace("\n", "\r\n"),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType,
                            ContentType.MultiPart.Mixed.withParameter("boundary", "boundary").toString()
                        )
                    )
                }
            })
        matrixRestClient.federation.downloadThumbnail("mediaId123", 64, 64, SCALE) { media ->
            media.shouldBeInstanceOf<Media.Stream>()
            media.contentLength shouldBe 22
            media.contentType shouldBe ContentType.Text.Plain
            media.contentDisposition shouldBe ContentDisposition("attachment").withParameter("filename", "example.txt")
            media.content.toByteArray().decodeToString() shouldBe "a multiline\r\ntext file"
        }.getOrThrow()
    }

    @Test
    fun shouldDownloadThumbnailRedirect() = runTest {
        val matrixRestClient = MatrixServerServerApiClientImpl(
            hostname = "hostname",
            getDelegatedDestination = { host, port -> host to port },
            sign = { Key.Ed25519Key("key", "value") },
            roomVersionStore = TestRoomVersionStore("12"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/federation/v1/media/thumbnail/mediaId123?width=64&height=64&method=scale",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                            --boundary
                            Content-Type: application/json

                            {}
                            --boundary
                            Location: https://example.org/mediablabla
                            
                            --boundary--

                        """.trimIndent().replace("\n", "\r\n"),
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType,
                            ContentType.MultiPart.Mixed.withParameter("boundary", "boundary").toString()
                        )
                    )
                }
            })
        matrixRestClient.federation.downloadThumbnail("mediaId123", 64, 64, SCALE) { media ->
            media.shouldBeInstanceOf<Media.Redirect>()
            media.location shouldBe "https://example.org/mediablabla"
        }.getOrThrow()
    }
}