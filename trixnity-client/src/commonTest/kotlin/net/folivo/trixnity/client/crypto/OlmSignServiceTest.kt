package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.instanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.crypto.VerificationState.*
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility

class OlmSignServiceTest : ShouldSpec({

    val json = createMatrixJson()
    val account = OlmAccount.create()
    val signingAccount = OlmAccount.create()
    val utility = OlmUtility.create()
    val bob = UserId("bob", "server")
    val store = mockk<Store> {
        every { this@mockk.account.userId } returns MutableStateFlow(UserId("me", "server"))
        every { this@mockk.account.deviceId } returns MutableStateFlow("ABCDEF")
        coEvery { deviceKeys.get(bob) } returns mapOf(
            "BBBBBB" to DeviceKeys(
                bob,
                "BBBBBB",
                setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keysOf(
                    Ed25519Key("BBBBBB", signingAccount.identityKeys.ed25519),
                    Curve25519Key("BBBBBB", signingAccount.identityKeys.curve25519)
                )
            )
        )
    }

    val cut = OlmSignService(json, store, account, utility)
    val signingAccountSignService = OlmSignService(json, mockk {
        every { this@mockk.account.userId } returns MutableStateFlow(UserId("bob", "server"))
        every { this@mockk.account.deviceId } returns MutableStateFlow("BBBBBB")
    }, signingAccount, utility)

    afterSpec {
        account.free()
        signingAccount.free()
        utility.free()
    }

    context(OlmSignService::signatures.name) {
        should("return signatures") {
            val result = cut.signatures(JsonObject(mapOf("key" to JsonPrimitive("value"))))
            result shouldHaveSize 1
            assertSoftly(result.entries.first()) {
                key shouldBe UserId("me", "server")
                value.keys shouldHaveSize 1
                assertSoftly(value.keys.first()) {
                    this shouldBe instanceOf<Ed25519Key>()
                    require(this is Ed25519Key)
                    keyId shouldBe "ABCDEF"
                    value shouldNot beBlank()
                }
            }
        }
        should("ignore unsigned and signature field") {
            val result1 = cut.signatures(JsonObject(mapOf("key" to JsonPrimitive("value"))))
            val result2 = cut.signatures(
                JsonObject(
                    mapOf(
                        "key" to JsonPrimitive("value"),
                        "signatures" to JsonPrimitive("value"),
                        "unsigned" to JsonPrimitive("value"),
                    )
                )
            )

            result1 shouldBe result2
        }
    }
    context("sign") {
        should("return signed object") {
            val event = Event.StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("room", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
            val result = cut.sign(event)
            result.signed shouldBe event
            assertSoftly(result.signatures.entries.first()) {
                key shouldBe UserId("me", "server")
                value.keys shouldHaveSize 1
                assertSoftly(value.keys.first()) {
                    this shouldBe instanceOf<Ed25519Key>()
                    require(this is Ed25519Key)
                    keyId shouldBe "ABCDEF"
                    value shouldNot beBlank()
                }
            }
        }
        should("ignore unsigned field") {
            val event1 = Event.StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("room", "server"),
                originTimestamp = 24,
                stateKey = ""
            )
            val event2 = Event.StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("room", "server"),
                originTimestamp = 24,
                unsigned = UnsignedStateEventData(1234),
                stateKey = ""
            )
            val result1 = cut.sign(event1)
            val result2 = cut.sign(event2)
            result1.signatures shouldBe result2.signatures
        }
    }
    context(OlmSignService::signCurve25519Key.name) {
        should("sign") {
            cut.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            ).signatures shouldHaveSize 1
        }
    }
    context("verify") {
        should("return valid") {
            val signedObject = signingAccountSignService.sign(
                Event.StateEvent(
                    NameEventContent("room name"),
                    EventId("\$eventId"),
                    UserId("their", "server"),
                    RoomId("room", "server"),
                    originTimestamp = 24,
                    unsigned = UnsignedStateEventData(1234),
                    stateKey = ""
                )
            )
            cut.verify(signedObject) shouldBe KeyVerificationState.Valid
        }
        should("verify SignedCurve25519Key") {
            val signedObject = signingAccountSignService.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            )
            cut.verify(signedObject) shouldBe KeyVerificationState.Valid
        }
        should("return invalid") {
            val signedObject = Signed(
                Event.StateEvent(
                    NameEventContent("room name"),
                    EventId("\$eventId"),
                    UserId("their", "server"),
                    RoomId("room", "server"),
                    originTimestamp = 24,
                    unsigned = UnsignedStateEventData(1234),
                    stateKey = ""
                ),
                mapOf(
                    UserId("bob", "server") to keysOf(
                        Ed25519Key(
                            "BBBBBB",
                            "qAwmMiFdBqJNVFnOcmIT1aIesjiecn6XHzutQZq2hGy1Z65FP7cMXRqarE/v9EinolFdli143bqwsl31fSPwBg"
                        )
                    )
                )
            )
            cut.verify(signedObject) shouldBe KeyVerificationState.Invalid("BAD_MESSAGE_MAC")
        }
        context("device keys") {
            should("return valid") {
                val toBeSigned = DeviceKeys(
                    userId = UserId("me", "server"),
                    deviceId = "ABCDEF",
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys = Keys(
                        keysOf(
                            Ed25519Key("ABCDEF", account.identityKeys.ed25519),
                            Curve25519Key("ABCDEF", account.identityKeys.curve25519)
                        )
                    ),
                )
                val signed = cut.sign(toBeSigned)
                cut.verify(
                    Signed(signed.signed, signed.signatures)
                ) shouldBe KeyVerificationState.Valid
            }
            should("return invalid") {
                cut.verify(
                    Signed(
                        DeviceKeys(
                            userId = UserId("me", "server"),
                            deviceId = "ABCDEF",
                            algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                            keys = Keys(
                                keysOf(
                                    Ed25519Key("ABCDEF", "dQNKytk1H42od+JZAyjGtJS0IRIC5y9S8eE3peuV8Ew"),
                                    Curve25519Key("ABCDEF", "CW6OxKFraxGEdMJsv/D+AcsExtyF3AZf/vi2h7l2tmU")
                                )
                            ),
                        ),
                        mapOf(
                            UserId("me", "server") to keysOf(
                                Ed25519Key(
                                    "ABCDEF",
                                    "qAwmMiFdBqJNVFnOcmIT1aIesjiecn6XHzutQZq2hGy1Z65FP7cMXRqarE/v9EinolFdli143bqwsl31fSPwBg"
                                )
                            )
                        )
                    )
                ) shouldBe KeyVerificationState.Invalid("BAD_MESSAGE_MAC")
            }
        }
    }
    context(OlmSignService::verifyEncryptedMegolm.name) {
        should("be ${Invalid::class.simpleName} when no key found") {
            val senderKey = Curve25519Key("BBBBBB", "keykeykey")
            val event = Event.MessageEvent(
                EncryptedEventContent.MegolmEncryptedEventContent("cipher cipher", senderKey, "BBBBBB", "sessionId"),
                EventId("$1event"),
                bob,
                RoomId("room", "server"),
                1234
            )
            cut.verifyEncryptedMegolm(event)::class shouldBe Invalid::class
        }
        should("be ${Valid::class.simpleName} when key found, but not marked as verified") {
            coEvery { store.deviceKeys.isVerified(any(), any(), any()) } returns false
            val event = Event.MessageEvent(
                EncryptedEventContent.MegolmEncryptedEventContent(
                    "cipher cipher",
                    Curve25519Key("BBBBBB", signingAccount.identityKeys.curve25519),
                    "BBBBBB",
                    "sessionId"
                ),
                EventId("$1event"),
                bob,
                RoomId("room", "server"),
                1234
            )
            cut.verifyEncryptedMegolm(event) shouldBe Valid
        }
        should("be ${Verified::class.simpleName} when key found and marked as verified") {
            coEvery { store.deviceKeys.isVerified(any(), any(), any()) } returns true
            val event = Event.MessageEvent(
                EncryptedEventContent.MegolmEncryptedEventContent(
                    "cipher cipher",
                    Curve25519Key("BBBBBB", signingAccount.identityKeys.curve25519),
                    "BBBBBB",
                    "sessionId"
                ),
                EventId("$1event"),
                bob,
                RoomId("room", "server"),
                1234
            )
            cut.verifyEncryptedMegolm(event) shouldBe Verified
        }
    }
})