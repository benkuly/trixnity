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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.clientserverapi.model.push.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule
import net.folivo.trixnity.core.model.push.PushRuleKind
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class PushRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixEventJson()
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: PushApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixAccessTokenAuth {
                authenticationFunction = { AccessTokenAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    pushApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

    @Test
    fun shouldGetPushers() = testApplication {
        initCut()
        everySuspending { handlerMock.getPushers(isAny()) }
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
        verifyWithSuspend {
            handlerMock.getPushers(isAny())
        }
    }

    @Test
    fun shouldSetPushers() = testApplication {
        initCut()
        everySuspending { handlerMock.setPushers(isAny()) }
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
        verifyWithSuspend {
            handlerMock.setPushers(assert {
                it.requestBody shouldBe SetPushers.Request(
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
    fun shouldGetNotifications() = testApplication {
        initCut()
        everySuspending { handlerMock.getNotifications(isAny()) }
            .returns(
                GetNotifications.Response(
                    nextToken = "abcdef",
                    notifications = listOf(
                        GetNotifications.Response.Notification(
                            actions = setOf(PushAction.Notify, PushAction.SetSoundTweak("default")),
                            event = Event.MessageEvent(
                                content = RoomMessageEventContent.TextMessageEventContent("body"),
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
                        "sender":"@example:example.org",
                        "room_id":"!jEsUZKDJdhlrceRyVU:example.org",
                        "origin_server_ts":1432735824653,
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.getPushRules(isAny()) }
            .returns(
                GetPushRules.Response(
                    global = mapOf(
                        PushRuleKind.CONTENT to listOf(
                            PushRule(
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
                        PushRuleKind.OVERRIDE to listOf(
                            PushRule(
                                actions = setOf(PushAction.DontNotify),
                                conditions = setOf(),
                                default = true,
                                enabled = false,
                                ruleId = ".m.rule.master"
                            ),
                            PushRule(
                                actions = setOf(PushAction.DontNotify),
                                conditions = setOf(PushCondition.EventMatch("content.msgtype", "m.notice")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.suppress_notices"
                            )
                        ),
                        PushRuleKind.ROOM to listOf(),
                        PushRuleKind.SENDER to listOf(),
                        PushRuleKind.UNDERRIDE to listOf(
                            PushRule(
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
                            PushRule(
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
                            PushRule(
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
                            PushRule(
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
                            PushRule(
                                actions = setOf(PushAction.Notify, PushAction.SetHighlightTweak(false)),
                                conditions = setOf(PushCondition.EventMatch("type", "m.room.member")),
                                default = true,
                                enabled = true,
                                ruleId = ".m.rule.member_event"
                            ),
                            PushRule(
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
                    "override": [
                      {
                        "actions": [
                          "dont_notify"
                        ],
                        "conditions": [],
                        "default": true,
                        "enabled": false,
                        "rule_id": ".m.rule.master"
                      },
                      {
                        "actions": [
                          "dont_notify"
                        ],
                        "conditions": [
                          {
                            "key": "content.msgtype",
                            "pattern": "m.notice",
                            "kind": "event_match"
                          }
                        ],
                        "default": true,
                        "enabled": true,
                        "rule_id": ".m.rule.suppress_notices"
                      }
                    ],
                    "room": [],
                    "sender": [],
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
                            "pattern": "m.call.invite",
                            "kind": "event_match"
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
                            "pattern": "m.room.message",
                            "kind": "event_match"
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
                            "pattern": "m.room.member",
                            "kind": "event_match"
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
                            "pattern": "m.room.message",
                            "kind": "event_match"
                          }
                        ],
                        "default": true,
                        "enabled": true,
                        "rule_id": ".m.rule.message"
                      }
                    ]
                  }
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
            handlerMock.getPushRules(isAny())
        }
    }

    @Test
    fun shouldGetPushRule() = testApplication {
        initCut()
        everySuspending { handlerMock.getPushRule(isAny()) }
            .returns(
                PushRule(
                    actions = setOf(PushAction.DontNotify),
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
                  "actions":[
                    "dont_notify"
                  ],
                  "default":false,
                  "enabled":true,
                  "pattern":"cake*lie",
                  "rule_id":"nocake"
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.setPushRule(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.deletePushRule(isAny()) }
            .returns(Unit)
        val response = client.delete("/_matrix/client/v3/pushrules/scope/content/ruleId") {
            bearerAuth("token")
        }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe "{}"
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.getPushRuleActions(isAny()) }
            .returns(GetPushRuleActions.Response(setOf(PushAction.DontNotify)))
        val response =
            client.get("/_matrix/client/v3/pushrules/scope/content/ruleId/actions") { bearerAuth("token") }
        assertSoftly(response) {
            this.status shouldBe HttpStatusCode.OK
            this.contentType() shouldBe ContentType.Application.Json.withCharset(Charsets.UTF_8)
            this.body<String>() shouldBe """
               {
                  "actions":[
                    "dont_notify"
                  ]
                }
            """.trimToFlatJson()
        }
        verifyWithSuspend {
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
        everySuspending { handlerMock.setPushRuleActions(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.getPushRuleEnabled(isAny()) }
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
        verifyWithSuspend {
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
        everySuspending { handlerMock.setPushRuleEnabled(isAny()) }
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
        verifyWithSuspend {
            handlerMock.setPushRuleEnabled(assert {
                it.endpoint.scope shouldBe "scope"
                it.endpoint.kind shouldBe PushRuleKind.CONTENT
                it.endpoint.ruleId shouldBe "ruleId"
                it.requestBody shouldBe SetPushRuleEnabled.Request(false)
            })
        }
    }
}
