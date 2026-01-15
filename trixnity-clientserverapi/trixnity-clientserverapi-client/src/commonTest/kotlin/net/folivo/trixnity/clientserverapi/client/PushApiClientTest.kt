package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction.*
import net.folivo.trixnity.core.model.push.PushCondition.*
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.model.push.PushRuleSet
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.testutils.scopedMockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class PushApiClientTest : TrixnityBaseTest() {

    @Test
    fun shouldGetPushers() = runTest {
        val response = """
            {
              "pushers": [
                {
                  "app_display_name": "Appy McAppface",
                  "app_id": "face.mcapp.appy.prod",
                  "data": {
                    "format": "format",
                    "url": "https://example.com/_matrix/push/v1/notify"
                  },
                  "device_display_name": "Alice's Phone",
                  "kind": "http",
                  "lang": "en-US",
                  "profile_tag": "xyz",
                  "pushkey": "Xp/MzCt8/9DcSNE9cuiaoT5Ac55job3TdLSSmtmYl4A="
                }
              ]
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/pushers", request.url.fullPath)
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getPushers().getOrThrow()
        assertEquals(
            GetPushers.Response(
                listOf(
                    GetPushers.Response.Pusher(
                        appDisplayName = "Appy McAppface",
                        appId = "face.mcapp.appy.prod",
                        data = PusherData(
                            format = "format",
                            url = "https://example.com/_matrix/push/v1/notify"
                        ),
                        deviceDisplayName = "Alice's Phone",
                        kind = "http",
                        lang = "en-US",
                        profileTag = "xyz",
                        pushkey = "Xp/MzCt8/9DcSNE9cuiaoT5Ac55job3TdLSSmtmYl4A="
                    )
                )
            ),
            result
        )
    }

    @Test
    fun shouldSetPushersSet() = runTest {
        val expectedRequest = """
            {
              "app_id": "com.example.app.ios",
              "pushkey": "APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ",
              "kind": "http",
              "app_display_name": "Mat Rix",
              "device_display_name": "EiPhone 9",
              "lang": "en",
              "data": {
                "format": "event_id_only",
                "url": "https://push-gateway.location.here/_matrix/push/v1/notify",
                "custom": "dino"
              },
              "append": false,
              "profile_tag": "xxyyzz"
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/pushers/set", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.setPushers(
            SetPushers.Request.Set(
                appDisplayName = "Mat Rix",
                appId = "com.example.app.ios",
                append = false,
                data = PusherData(
                    format = "event_id_only",
                    url = "https://push-gateway.location.here/_matrix/push/v1/notify",
                    customFields = buildJsonObject { put("custom", "dino") }
                ),
                deviceDisplayName = "EiPhone 9",
                kind = "http",
                lang = "en",
                profileTag = "xxyyzz",
                pushkey = "APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ"
            )
        ).getOrThrow()
    }

    @Test
    fun shouldSetPushersRemove() = runTest {
        val expectedRequest = """
            {
              "app_id":"com.example.app.ios",
              "pushkey":"APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ",
              "kind":null
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals("/_matrix/client/v3/pushers/set", request.url.fullPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.setPushers(
            SetPushers.Request.Remove(
                appId = "com.example.app.ios",
                pushkey = "APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ"
            )
        ).getOrThrow()
    }

    @Test
    fun shouldGetNotifications() = runTest {
        val response = """
            {
              "next_token": "abcdef",
              "notifications": [
                {
                  "actions": [
                    "notify",
                    {
                        "set_tweak": "sound",
                        "value": "default"
                    }
                  ],
                  "event": {
                    "content": {
                      "body": "body",
                      "msgtype": "m.text"
                    },
                    "event_id": "$143273582443PhrSn:example.org",
                    "origin_server_ts": 1432735824653,
                    "room_id": "!jEsUZKDJdhlrceRyVU:example.org",
                    "sender": "@example:example.org",
                    "type": "m.room.message"
                  },
                  "profile_tag": "hcbvkzxhcvb",
                  "read": true,
                  "room_id": "!abcdefg:example.com",
                  "ts": 1475508881945
                }
              ]
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/notifications?from=from&limit=24&only=only",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getNotifications("from", 24, "only").getOrThrow()
        assertEquals(
            GetNotifications.Response(
                nextToken = "abcdef",
                notifications = listOf(
                    GetNotifications.Response.Notification(
                        actions = setOf(Notify, SetSoundTweak("default")),
                        event = MessageEvent(
                            content = RoomMessageEventContent.TextBased.Text("body"),
                            id = EventId("\$143273582443PhrSn:example.org"),
                            originTimestamp = 1432735824653,
                            roomId = RoomId("!jEsUZKDJdhlrceRyVU:example.org"),
                            sender = UserId("@example:example.org"),
                        ),
                        profileTag = "hcbvkzxhcvb",
                        read = true,
                        roomId = RoomId("!abcdefg:example.com"),
                        timestamp = 1475508881945
                    )
                )
            ),
            result
        )
    }

    @Test
    fun shouldGetPushRules() = runTest {
        val response = """
            {
              "global": {
                "override": [
                  {
                    "actions": [
                    ],
                    "conditions": [],
                    "default": true,
                    "enabled": false,
                    "rule_id": ".m.rule.master"
                  },
                  {
                    "actions": [
                    ],
                    "conditions": [
                      {
                        "key": "content.msgtype",
                        "kind": "event_match",
                        "pattern": "m.notice"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.suppress_notices"
                  }
                ],
                "content": [
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "sound",
                        "value": "default"
                      },
                      {
                        "set_tweak": "highlight"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "pattern": "alice",
                    "rule_id": ".m.rule.contains_user_name"
                  }
                ],
                "underride": [
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "sound",
                        "value": "ring"
                      },
                      {
                        "set_tweak": "highlight",
                        "value": false
                      }
                    ],
                    "conditions": [
                      {
                        "key": "type",
                        "kind": "event_match",
                        "pattern": "m.call.invite"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.call"
                  },
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "sound",
                        "value": "default"
                      },
                      {
                        "set_tweak": "highlight"
                      }
                    ],
                    "conditions": [
                      {
                        "kind": "contains_display_name"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.contains_display_name"
                  },
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "sound",
                        "value": "default"
                      },
                      {
                        "set_tweak": "highlight",
                        "value": false
                      }
                    ],
                    "conditions": [
                      {
                        "is": "2",
                        "kind": "room_member_count"
                      },
                      {
                        "key": "type",
                        "kind": "event_match",
                        "pattern": "m.room.message"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.room_one_to_one"
                  },
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "sound",
                        "value": "default"
                      },
                      {
                        "set_tweak": "highlight",
                        "value": false
                      }
                    ],
                    "conditions": [
                      {
                        "key": "type",
                        "kind": "event_match",
                        "pattern": "m.room.member"
                      },
                      {
                        "key": "content.membership",
                        "kind": "event_match",
                        "pattern": "invite"
                      },
                      {
                        "key": "state_key",
                        "kind": "event_match",
                        "pattern": "@alice:example.com"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.invite_for_me"
                  },
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "highlight",
                        "value": false
                      }
                    ],
                    "conditions": [
                      {
                        "key": "type",
                        "kind": "event_match",
                        "pattern": "m.room.member"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.member_event"
                  },
                  {
                    "actions": [
                      "notify",
                      {
                        "set_tweak": "highlight",
                        "value": false
                      }
                    ],
                    "conditions": [
                      {
                        "key": "type",
                        "kind": "event_match",
                        "pattern": "m.room.message"
                      }
                    ],
                    "default": true,
                    "enabled": true,
                    "rule_id": ".m.rule.message"
                  }
                ]
              }
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getPushRules().getOrThrow()
        assertEquals(
            GetPushRules.Response(
                global = PushRuleSet(
                    content = listOf(
                        PushRule.Content(
                            actions = setOf(
                                Notify,
                                SetSoundTweak("default"),
                                SetHighlightTweak()
                            ),
                            default = true,
                            enabled = true,
                            pattern = "alice",
                            ruleId = ".m.rule.contains_user_name"
                        )
                    ),
                    override = listOf(
                        PushRule.Override(
                            actions = setOf(),
                            conditions = setOf(),
                            default = true,
                            enabled = false,
                            ruleId = ".m.rule.master"
                        ),
                        PushRule.Override(
                            actions = setOf(),
                            conditions = setOf(EventMatch("content.msgtype", "m.notice")),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.suppress_notices"
                        )
                    ),
                    underride = listOf(
                        PushRule.Underride(
                            actions = setOf(
                                Notify,
                                SetSoundTweak("ring"),
                                SetHighlightTweak(false)
                            ),
                            conditions = setOf(EventMatch("type", "m.call.invite")),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.call"
                        ),
                        PushRule.Underride(
                            actions = setOf(
                                Notify,
                                SetSoundTweak("default"),
                                SetHighlightTweak()
                            ),
                            conditions = @Suppress("DEPRECATION") setOf(ContainsDisplayName),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.contains_display_name"
                        ),
                        PushRule.Underride(
                            actions = setOf(
                                Notify,
                                SetSoundTweak("default"),
                                SetHighlightTweak(false)
                            ),
                            conditions = setOf(
                                RoomMemberCount("2"),
                                EventMatch("type", "m.room.message")
                            ),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.room_one_to_one"
                        ),
                        PushRule.Underride(
                            actions = setOf(
                                Notify,
                                SetSoundTweak("default"),
                                SetHighlightTweak(false)
                            ),
                            conditions = setOf(
                                EventMatch("type", "m.room.member"),
                                EventMatch("content.membership", "invite"),
                                EventMatch("state_key", "@alice:example.com")
                            ),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.invite_for_me"
                        ),
                        PushRule.Underride(
                            actions = setOf(Notify, SetHighlightTweak(false)),
                            conditions = setOf(EventMatch("type", "m.room.member")),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.member_event"
                        ),
                        PushRule.Underride(
                            actions = setOf(Notify, SetHighlightTweak(false)),
                            conditions = setOf(EventMatch("type", "m.room.message")),
                            default = true,
                            enabled = true,
                            ruleId = ".m.rule.message"
                        ),
                    )
                )
            ),
            result
        )
    }

    @Test
    fun shouldGetPushRule() = runTest {
        val response = """
            {
              "actions": [
                "notify"
              ],
              "default": false,
              "enabled": true,
              "pattern": "cake*lie",
              "rule_id": "nocake"
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getPushRule("scope", PushRuleKind.CONTENT, "ruleId").getOrThrow()
        assertEquals(
            PushRule.Content(
                actions = setOf(Notify),
                default = false,
                enabled = true,
                pattern = "cake*lie",
                ruleId = "nocake"
            ),
            result
        )
    }

    @Test
    fun shouldSetPushRule() = runTest {
        val expectedRequest = """
            {
              "actions":[
                "notify",
                {
                    "set_tweak":"sound",
                    "value":"default"
                 }
              ],
              "conditions":[
                {
                    "key":"type",
                    "pattern":"m.room.member",
                    "kind":"event_match"
                }
              ],
              "pattern":"cake*lie"
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId?before=before&after=after",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.setPushRule(
            scope = "scope",
            kind = PushRuleKind.CONTENT,
            ruleId = "ruleId",
            pushRule = SetPushRule.Request(
                actions = setOf(Notify, SetSoundTweak("default")),
                conditions = setOf(EventMatch("type", "m.room.member")),
                pattern = "cake*lie"
            ),
            beforeRuleId = "before",
            afterRuleId = "after"
        ).getOrThrow()
    }

    @Test
    fun shouldDeletePushRule() = runTest {
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Delete, request.method)
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.deletePushRule(
            scope = "scope",
            kind = PushRuleKind.CONTENT,
            ruleId = "ruleId",
        ).getOrThrow()
    }

    @Test
    fun shouldGetPushRuleActions() = runTest {
        val response = """
            {
              "actions": [
                "notify"
              ]
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId/actions",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getPushRuleActions("scope", PushRuleKind.CONTENT, "ruleId").getOrThrow()
        assertEquals(setOf(Notify), result)
    }

    @Test
    fun shouldSetPushRuleActions() = runTest {
        val expectedRequest = """
            {
              "actions":[
                "notify",
                {
                    "set_tweak":"sound",
                    "value":"default"
                 }
              ]
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId/actions",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.setPushRuleActions(
            scope = "scope",
            kind = PushRuleKind.CONTENT,
            ruleId = "ruleId",
            actions = setOf(Notify, SetSoundTweak("default")),
        ).getOrThrow()
    }

    @Test
    fun shouldGetPushRuleEnabled() = runTest {
        val response = """
            {
              "enabled": true
            }
        """.trimIndent()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId/enabled",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Get, request.method)
                    respond(
                        response,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        val result = matrixRestClient.push.getPushRuleEnabled("scope", PushRuleKind.CONTENT, "ruleId").getOrThrow()
        assertEquals(true, result)
    }

    @Test
    fun shouldSetPushRuleEnabled() = runTest {
        val expectedRequest = """
            {
              "enabled":false
            }
        """.trimToFlatJson()
        val matrixRestClient = MatrixClientServerApiClientImpl(
            baseUrl = Url("https://matrix.host"),
            httpClientEngine = scopedMockEngine {
                addHandler { request ->
                    assertEquals(
                        "/_matrix/client/v3/pushrules/scope/content/ruleId/enabled",
                        request.url.fullPath
                    )
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals(expectedRequest, request.body.toByteArray().decodeToString())
                    respond(
                        "{}",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            })
        matrixRestClient.push.setPushRuleEnabled(
            scope = "scope",
            kind = PushRuleKind.CONTENT,
            ruleId = "ruleId",
            enabled = false
        ).getOrThrow()
    }
}
