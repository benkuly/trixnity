package net.folivo.trixnity.clientserverapi.client

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.server.GetCapabilities
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import net.folivo.trixnity.clientserverapi.model.server.Search
import net.folivo.trixnity.clientserverapi.model.server.WhoIs
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.testutils.mockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerApiClientTest {
    @Test
    fun shouldGetVersions() = runTest {
        val response = GetVersions.Response(
            versions = emptyList(),
            unstable_features = mapOf()
        )
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/versions", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getVersions().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldGetCapabilities() = runTest {
        val response = GetCapabilities.Response(
            capabilities = GetCapabilities.Response.Capabilities(
                GetCapabilities.Response.Capabilities.ChangePasswordCapability(true),
                GetCapabilities.Response.Capabilities.RoomVersionsCapability("5", mapOf())
            )
        )
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/capabilities", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        Json.encodeToString(response),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.server.getCapabilities().getOrThrow()
        assertEquals(response, result)
    }

    @Test
    fun shouldSearch() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/search",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Post, request.method)
                    request.body.toByteArray().decodeToString() shouldBe """
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
                    """.trimToFlatJson()
                    respond(
                        """
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
                                          "$144429830826TWwbB:localhost"
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
                                        "event_id": "$144429830826TWwbB:localhost",
                                        "origin_server_ts": 1432735824653,
                                        "room_id": "!qPewotXpIctQySfjSy:localhost",
                                        "sender": "@example:example.org",
                                        "type": "m.room.message"
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.server.search(
            request = Search.Request(
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
        ).getOrThrow() shouldBe Search.Response(
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
                            result = ClientEvent.MessageEvent(
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
    }

    @Test
    fun shouldWhoIs() = runTest {
        val matrixRestClient = MatrixClientServerApiClient(
            baseUrl = Url("https://matrix.host"),
            httpClientFactory = mockEngineFactory {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/admin/whois/@peter:rabbit.rocks", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        """
                           {
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
                             },
                             "user_id": "@peter:rabbit.rocks"
                           }
                       """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, Application.Json.toString())
                    )
                }
            })
        matrixRestClient.server.whoIs(UserId("@peter:rabbit.rocks")).getOrThrow() shouldBe WhoIs.Response(
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
    }
}