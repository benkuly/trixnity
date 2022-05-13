package net.folivo.trixnity.serverserverapi.server

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.Charsets.UTF_8
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralDataUnit
import net.folivo.trixnity.core.model.events.PersistentDataUnit
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.serverserverapi.model.transaction.SendTransaction
import net.folivo.trixnity.serverserverapi.model.transaction.SendTransaction.Response.PDUProcessingResult
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class TransactionRoutesTest : TestsWithMocks() {
    override fun setUpMocks() = injectMocks(mocker)

    private val json = createMatrixDataUnitJson({ "3" })
    private val mapping = createEventContentSerializerMappings()

    @Mock
    lateinit var handlerMock: TransactionApiHandler

    private fun ApplicationTestBuilder.initCut() {
        application {
            installMatrixSignatureAuth(hostname = "") {
                authenticationFunction = { SignatureAuthenticationFunctionResult(UserIdPrincipal("user"), null) }
            }
            matrixApiServer(json) {
                routing {
                    transactionApiRoutes(handlerMock, json, mapping)
                }
            }
        }
    }

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
                        "presence": "online"
                      },
                      "edu_type": "m.presence"
                    }
                  ],
                  "origin": "matrix.org",
                  "origin_server_ts": 1234567890,
                  "pdus": [
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
                      "signatures": {
                          "matrix.org": {
                            "ed25519:key": "these86bytesofbase64signaturecoveressentialfieldsincludinghashessocancheckredactedpdus"
                          }
                      },
                      "type": "m.room.message"
                    }
                  ]
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
                    edus = listOf(EphemeralDataUnit(PresenceEventContent(PresenceEventContent.Presence.ONLINE))),
                    origin = "matrix.org",
                    originTimestamp = 1234567890,
                    pdus = listOf(
                        Signed(
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
                    )
                )
            })
        }
    }
}