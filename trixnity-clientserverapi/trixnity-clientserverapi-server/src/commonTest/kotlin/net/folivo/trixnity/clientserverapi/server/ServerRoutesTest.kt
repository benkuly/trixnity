package net.folivo.trixnity.clientserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class ServerRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: ServerApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    serverApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @Test
    fun shouldGetVersions() = testApplication {
        initCut()
        everySuspending { handlerMock.getVersions(isAny()) }
            .returns(
                GetVersions.Response(
                    versions = emptyList(),
                    unstable_features = mapOf()
                )
            )
        val response = client.get("/_matrix/client/versions") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {"versions":[],"unstable_features":{}}
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getVersions(isAny())
        }
    }

    @Test
    fun shouldGetCapabilities() = testApplication {
        initCut()
        everySuspending { handlerMock.getCapabilities(isAny()) }
            .returns(
                GetCapabilities.Response(
                    capabilities = GetCapabilities.Response.Capabilities(
                        GetCapabilities.Response.Capabilities.ChangePasswordCapability(true),
                        GetCapabilities.Response.Capabilities.RoomVersionsCapability("5", mapOf())
                    )
                )
            )
        val response = client.get("/_matrix/client/v3/capabilities") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "capabilities": {
                    "m.change_password": {
                      "enabled": true
                    },
                    "m.room_versions": {
                      "default": "5",
                      "available": {}
                    },
                    "m.set_displayname": {
                      "enabled": true
                    },
                    "m.set_avatar_url": {
                      "enabled": true
                    },
                    "m.3pid_changes": {
                      "enabled": true
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getCapabilities(isAny())
        }
    }

    @Test
    fun shouldSearch() = testApplication {
        initCut()
        everySuspending { handlerMock.search(isAny()) }
            .returns(
                Search.Response(
                    Search.Response.ResultCategories(
                        Search.Response.ResultCategories.RoomEventsResult(
                            count = 1224,
                            groups = mapOf(
                                "room_id" to mapOf(
                                    "!qPewotXpIctQySfjSy:localhost" to Search.Response.ResultCategories.RoomEventsResult.GroupValue(
                                        nextBatch = "BdgFsdfHSf-dsFD",
                                        order = 1,
                                        results = listOf("$144429830826TWwbB:localhost")
                                    )
                                )
                            ),
                            highlights = setOf("martians", "men"),
                            nextBatch = "5FdgFsd234dfgsdfFD",
                            results = listOf(
                                Search.Response.ResultCategories.RoomEventsResult.Results(
                                    rank = 0.00424866,
                                    result = Event.MessageEvent(
                                        RoomMessageEventContent.TextMessageEventContent("This is an example text message"),
                                        id = EventId("$144429830826TWwbB:localhost"),
                                        originTimestamp = 1432735824653,
                                        roomId = RoomId("!qPewotXpIctQySfjSy:localhost"),
                                        sender = UserId("@example:example.org")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        val response = client.post("/_matrix/client/v3/search") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "search_categories": {
                    "room_events": {
                      "groupings": {
                        "group_by": [
                          {
                            "key": "room_id"
                          }
                        ]
                      },
                      "keys": [
                        "content.body"
                      ],
                      "order_by": "recent",
                      "search_term": "martians and men"
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
                  "search_categories": {
                    "room_events": {
                      "count": 1224,
                      "groups": {
                        "room_id": {
                          "!qPewotXpIctQySfjSy:localhost": {
                            "next_batch": "BdgFsdfHSf-dsFD",
                            "order": 1,
                            "results": [
                              "${'$'}144429830826TWwbB:localhost"
                            ]
                          }
                        }
                      },
                      "highlights": [
                        "martians",
                        "men"
                      ],
                      "next_batch": "5FdgFsd234dfgsdfFD",
                      "results": [
                        {
                          "rank": 0.00424866,
                          "result": {
                            "content": {
                              "body": "This is an example text message",
                              "msgtype": "m.text"
                            },
                            "event_id": "${'$'}144429830826TWwbB:localhost",
                            "sender": "@example:example.org",
                            "room_id": "!qPewotXpIctQySfjSy:localhost",
                            "origin_server_ts": 1432735824653,
                            "type": "m.room.message"
                          }
                        }
                      ]
                    }
                  }
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.search(assert {
                it.requestBody shouldBe Search.Request(
                    Search.Request.Categories(
                        Search.Request.Categories.RoomEventsCriteria(
                            groupings = Search.Request.Categories.RoomEventsCriteria.Groupings(
                                setOf(
                                    Search.Request.Categories.RoomEventsCriteria.Groupings.Groups(
                                        "room_id"
                                    )
                                )
                            ),
                            keys = setOf("content.body"),
                            orderBy = Search.Request.Categories.RoomEventsCriteria.Ordering.RECENT,
                            searchTerm = "martians and men"
                        )
                    )
                )
            })
        }
    }

    @Test
    fun shouldWhoIs() = testApplication {
        initCut()
        everySuspending { handlerMock.whoIs(isAny()) }
            .returns(
                WhoIs.Response(
                    userId = UserId("@peter:rabbit.rocks"),
                    devices = mapOf(
                        "teapot" to WhoIs.Response.DeviceInfo(
                            setOf(
                                WhoIs.Response.DeviceInfo.SessionInfo(
                                    setOf(
                                        WhoIs.Response.DeviceInfo.SessionInfo.ConnectionInfo(
                                            ip = "127.0.0.1",
                                            lastSeen = 1411996332123,
                                            userAgent = "curl/7.31.0-DEV"
                                        ),
                                        WhoIs.Response.DeviceInfo.SessionInfo.ConnectionInfo(
                                            ip = "10.0.0.2",
                                            lastSeen = 1411996332123,
                                            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        val response = client.get("/_matrix/client/v3/admin/whois/@peter:rabbit.rocks") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                 "user_id": "@peter:rabbit.rocks",
                 "devices": {
                   "teapot": {
                     "sessions": [
                       {
                         "connections": [
                           {
                             "ip": "127.0.0.1",
                             "last_seen": 1411996332123,
                             "user_agent": "curl/7.31.0-DEV"
                           },
                           {
                             "ip": "10.0.0.2",
                             "last_seen": 1411996332123,
                             "user_agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36"
                           }
                         ]
                       }
                     ]
                   }
                 }
               }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.whoIs(assert {
                it.endpoint.userId shouldBe UserId("@peter:rabbit.rocks")
            })
        }
    }
}
