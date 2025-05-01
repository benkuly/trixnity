package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.*
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.*
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PushRoutesTest : TrixnityBaseTest() {
    private val json = createMatrixEventJson()
    private val mapping = createDefaultEventContentSerializerMappings()

    val handlerMock = mock<PushApiHandler>()

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = AccessTokenAuthenticationFunction {
                    AccessTokenAuthenticationFunctionResult(
                        MatrixClientPrincipal(
                            UserId("user", "server"),
                            "deviceId"
                        ), null
                    )
                }
            }
            matrixApiServer(json) {
                pushApiRoutes(handlerMock, json, mapping)
            }
        }
    }

    @BeforeTest
    fun beforeTest() {
        resetAnswers(handlerMock)
        resetCalls(handlerMock)
    }

    @Test
    fun shouldGetPushers() = testApplication {
        initCut()
        everySuspend { handlerMock.getPushers(any()) }
            .returns(
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
                )
            )
        val response = client.get("/_matrix/client/v3/pushers") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "pushers":[
                    {
                      "app_display_name":"Appy McAppface",
                      "app_id":"face.mcapp.appy.prod",
                      "data":{
                        "format":"format",
                        "url":"https://example.com/_matrix/push/v1/notify"
                      },
                      "device_display_name":"Alice's Phone",
                      "kind":"http",
                      "lang":"en-US",
                      "profile_tag":"xyz",
                      "pushkey":"Xp/MzCt8/9DcSNE9cuiaoT5Ac55job3TdLSSmtmYl4A="
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPushers(any())
        }
    }

    @Test
    fun shouldSetPushersSet() = testApplication {
        initCut()
        everySuspend { handlerMock.setPushers(any()) }
            .returns(Unit)
        val response = client.post("/_matrix/client/v3/pushers/set") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "app_display_name":"Mat Rix",
                  "app_id":"com.example.app.ios",
                  "append":false,
                  "data":{
                    "format":"event_id_only",
                    "url":"https://push-gateway.location.here/_matrix/push/v1/notify",
                    "custom":"dino"
                  },
                  "device_display_name":"EiPhone 9",
                  "kind":"http",
                  "lang":"en",
                  "profile_tag":"xxyyzz",
                  "pushkey":"APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ"
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
            handlerMock.setPushers(assert {
                it.requestBody shouldBe SetPushers.Request.Set(
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
            })
        }
    }

    @Test
    fun shouldSetPushersRemove() = testApplication {
        initCut()
        everySuspend { handlerMock.setPushers(any()) }
            .returns(Unit)
        val response = client.post("/_matrix/client/v3/pushers/set") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "app_id":"com.example.app.ios",
                  "kind":null,
                  "pushkey":"APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ"
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
            handlerMock.setPushers(assert {
                it.requestBody shouldBe SetPushers.Request.Remove(
                    appId = "com.example.app.ios",
                    pushkey = "APA91bHPRgkF3JUikC4ENAHEeMrd41Zxv3hVZjC9KtT8OvPVGJ-hQMRKRrZuJAEcl7B338qju59zJMjw2DELjzEvxwYv7hH5Ynpc1ODQ0aT4U4OFEeco8ohsN5PjL1iC2dNtk2BAokeMCg2ZXKqpc8FXKmhX94kIxQ"
                )
            })
        }
    }

    @Test
    fun shouldGetNotifications() = testApplication {
        initCut()
        everySuspend { handlerMock.getNotifications(any()) }
            .returns(
                GetNotifications.Response(
                    nextToken = "abcdef",
                    notifications = listOf(
                        GetNotifications.Response.Notification(
                            actions = setOf(PushAction.Notify, PushAction.SetSoundTweak("default")),
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
                )
            )
        val response =
            client.get("/_matrix/client/v3/notifications?from=from&limit=24&only=only") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "next_token":"abcdef",
                  "notifications":[
                    {
                      "actions":[
                        "notify",
                        {
                            "set_tweak":"sound",
                            "value":"default"
                        }
                      ],
                      "event":{
                        "content":{
                          "body":"body",
                          "msgtype":"m.text"
                        },
                        "event_id":"$143273582443PhrSn:example.org",
                        "origin_server_ts":1432735824653,
                        "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                        "sender":"@example:example.org",
                        "type":"m.room.message"
                      },
                      "profile_tag":"hcbvkzxhcvb",
                      "read":true,
                      "room_id":"!abcdefg:example.com",
                      "ts":1475508881945
                    }
                  ]
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getNotifications(assert {
                it.endpoint.from shouldBe "from"
                it.endpoint.limit shouldBe 24
                it.endpoint.only shouldBe "only"
            })
        }
    }

    @Test
    fun shouldGetPushRules() = testApplication {
        initCut()
        everySuspend { handlerMock.getPushRules(any()) }
            .returns(
                GetPushRules.Response(
                    PushRuleSet(
                        content = listOf(
                            PushRule.Content(
                                actions = setOf(
                                    PushAction.Notify,
                                    PushAction.SetSoundTweak("default"),
                                    PushAction.SetHighlightTweak()
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
                                conditions = setOf(PushCondition.EventMatch("content.msgtype", "m.notice")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.suppress_notices"
                            )
                        ),
                        underride = listOf(
                            PushRule.Underride(
                                actions = setOf(
                                    PushAction.Notify,
                                    PushAction.SetSoundTweak("ring"),
                                    PushAction.SetHighlightTweak(false)
                                ),
                                conditions = setOf(PushCondition.EventMatch("type", "m.call.invite")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.call"
                            ),
                            PushRule.Underride(
                                actions = setOf(
                                    PushAction.Notify,
                                    PushAction.SetSoundTweak("default"),
                                    PushAction.SetHighlightTweak()
                                ),
                                conditions = setOf(PushCondition.ContainsDisplayName),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.contains_display_name"
                            ),
                            PushRule.Underride(
                                actions = setOf(
                                    PushAction.Notify,
                                    PushAction.SetSoundTweak("default"),
                                    PushAction.SetHighlightTweak(false)
                                ),
                                conditions = setOf(
                                    PushCondition.RoomMemberCount("2"),
                                    PushCondition.EventMatch("type", "m.room.message")
                                ),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.room_one_to_one"
                            ),
                            PushRule.Underride(
                                actions = setOf(
                                    PushAction.Notify,
                                    PushAction.SetSoundTweak("default"),
                                    PushAction.SetHighlightTweak(false)
                                ),
                                conditions = setOf(
                                    PushCondition.EventMatch("type", "m.room.member"),
                                    PushCondition.EventMatch("content.membership", "invite"),
                                    PushCondition.EventMatch("state_key", "@alice:example.com")
                                ),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.invite_for_me"
                            ),
                            PushRule.Underride(
                                actions = setOf(PushAction.Notify, PushAction.SetHighlightTweak(false)),
                                conditions = setOf(PushCondition.EventMatch("type", "m.room.member")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.member_event"
                            ),
                            PushRule.Underride(
                                actions = setOf(PushAction.Notify, PushAction.SetHighlightTweak(false)),
                                conditions = setOf(PushCondition.EventMatch("type", "m.room.message")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.message"
                            ),
                        )
                    )
                )
            )
        val response =
            client.get("/_matrix/client/v3/pushrules/") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
                {
                  "global": {
                    "override": [
                      {
                        "rule_id": ".m.rule.master",
                        "default": true,
                        "enabled": false,
                        "actions": [],
                        "conditions": []
                      },
                      {
                        "rule_id": ".m.rule.suppress_notices",
                        "default": true,
                        "enabled": true,
                        "actions": [],
                        "conditions": [
                          {
                            "key": "content.msgtype",
                            "pattern": "m.notice",
                            "kind": "event_match"
                          }
                        ]
                      }
                    ],
                    "content": [
                      {
                        "rule_id": ".m.rule.contains_user_name",
                        "default": true,
                        "enabled": true,
                        "actions": [
                          "notify",
                          {
                            "set_tweak": "sound",
                            "value": "default"
                          },
                          {
                            "set_tweak": "highlight",
                            "value": true
                          }
                        ],
                        "pattern": "alice"
                      }
                    ],
                    "underride": [
                      {
                        "rule_id": ".m.rule.call",
                        "default": true,
                        "enabled": true,
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
                            "pattern": "m.call.invite",
                            "kind": "event_match"
                          }
                        ]
                      },
                      {
                        "rule_id": ".m.rule.contains_display_name",
                        "default": true,
                        "enabled": true,
                        "actions": [
                          "notify",
                          {
                            "set_tweak": "sound",
                            "value": "default"
                          },
                          {
                            "set_tweak": "highlight",
                            "value": true
                          }
                        ],
                        "conditions": [
                          {
                            "kind": "contains_display_name"
                          }
                        ]
                      },
                      {
                        "rule_id": ".m.rule.room_one_to_one",
                        "default": true,
                        "enabled": true,
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
                            "pattern": "m.room.message",
                            "kind": "event_match"
                          }
                        ]
                      },
                      {
                        "rule_id": ".m.rule.invite_for_me",
                        "default": true,
                        "enabled": true,
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
                            "pattern": "m.room.member",
                            "kind": "event_match"
                          },
                          {
                            "key": "content.membership",
                            "pattern": "invite",
                            "kind": "event_match"
                          },
                          {
                            "key": "state_key",
                            "pattern": "@alice:example.com",
                            "kind": "event_match"
                          }
                        ]
                      },
                      {
                        "rule_id": ".m.rule.member_event",
                        "default": true,
                        "enabled": true,
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
                            "pattern": "m.room.member",
                            "kind": "event_match"
                          }
                        ]
                      },
                      {
                        "rule_id": ".m.rule.message",
                        "default": true,
                        "enabled": true,
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
                            "pattern": "m.room.message",
                            "kind": "event_match"
                          }
                        ]
                      }
                    ]
                  }
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPushRules(any())
        }
    }

    @Test
    fun shouldGetPushRule() = testApplication {
        initCut()
        everySuspend { handlerMock.getPushRule(any()) }
            .returns(
                PushRule.Content(
                    actions = setOf(PushAction.Notify),
                    default = false,
                    enabled = true,
                    pattern = "cake*lie",
                    ruleId = "nocake"
                )
            )
        val response =
            client.get("/_matrix/client/v3/pushrules/scope/content/ruleId") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "rule_id":"nocake",
                  "default":false,
                  "enabled":true,
                  "actions":[
                    "notify"
                  ],
                  "pattern":"cake*lie"
               }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPushRule(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
            })
        }
    }

    @Test
    fun shouldSetPushRule() = testApplication {
        initCut()
        everySuspend { handlerMock.setPushRule(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/client/v3/pushrules/scope/content/ruleId?before=before&after=after") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
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
            """.trimIndent()
            )
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.setPushRule(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
                it.endpoint.beforeRuleId shouldBe "before"
                it.endpoint.afterRuleId shouldBe "after"
                it.requestBody shouldBe SetPushRule.Request(
                    actions = setOf(PushAction.Notify, PushAction.SetSoundTweak("default")),
                    conditions = setOf(PushCondition.EventMatch("type", "m.room.member")),
                    pattern = "cake*lie"
                )
            })
        }
    }

    @Test
    fun shouldDeletePushRule() = testApplication {
        initCut()
        everySuspend { handlerMock.deletePushRule(any()) }
            .returns(Unit)
        val response = client.delete("/_matrix/client/v3/pushrules/scope/content/ruleId") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifySuspend {
            handlerMock.deletePushRule(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
            })
        }
    }

    @Test
    fun shouldGetPushRuleActions() = testApplication {
        initCut()
        everySuspend { handlerMock.getPushRuleActions(any()) }
            .returns(GetPushRuleActions.Response(setOf(PushAction.Notify)))
        val response =
            client.get("/_matrix/client/v3/pushrules/scope/content/ruleId/actions") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "actions":[
                    "notify"
                  ]
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPushRuleActions(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
            })
        }
    }

    @Test
    fun shouldSetPushRuleActions() = testApplication {
        initCut()
        everySuspend { handlerMock.setPushRuleActions(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/client/v3/pushrules/scope/content/ruleId/actions") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "actions":[
                    "notify",
                    {
                        "set_tweak":"sound",
                        "value":"default"
                     }
                  ]
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
            handlerMock.setPushRuleActions(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
                it.requestBody shouldBe SetPushRuleActions.Request(
                    setOf(PushAction.Notify, PushAction.SetSoundTweak("default"))
                )
            })
        }
    }

    @Test
    fun shouldGetPushRuleEnabled() = testApplication {
        initCut()
        everySuspend { handlerMock.getPushRuleEnabled(any()) }
            .returns(GetPushRuleEnabled.Response(false))
        val response =
            client.get("/_matrix/client/v3/pushrules/scope/content/ruleId/enabled") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "enabled":false
                }
            """.trimToFlatJson()
        }
        verifySuspend {
            handlerMock.getPushRuleEnabled(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
            })
        }
    }

    @Test
    fun shouldSetPushRuleEnabled() = testApplication {
        initCut()
        everySuspend { handlerMock.setPushRuleEnabled(any()) }
            .returns(Unit)
        val response = client.put("/_matrix/client/v3/pushrules/scope/content/ruleId/enabled") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "enabled":false
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
            handlerMock.setPushRuleEnabled(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
                it.requestBody shouldBe SetPushRuleEnabled.Request(false)
            })
        }
    }
}
