package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.instanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.crypto.Key.Curve25519Key
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility

class OlmSignServiceTest : ShouldSpec({

    val json = createMatrixJson()
    val account = OlmAccount.create()
    val aliceSigningAccount = OlmAccount.create()
    val bobSigningAccount = OlmAccount.create()
    val utility = OlmUtility.create()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val store = mockk<Store>()

    lateinit var cut: OlmSignService
    lateinit var aliceSigningAccountSignService: OlmSignService
    lateinit var bobSigningAccountSignService: OlmSignService


    beforeTest {
        aliceSigningAccountSignService =
            OlmSignService(UserId("alice", "server"), "ALICE_DEVICE", json, mockk(), aliceSigningAccount, utility)
        bobSigningAccountSignService =
            OlmSignService(UserId("bob", "server"), "BBBBBB", json, mockk(), bobSigningAccount, utility)

        coEvery { store.keys.getDeviceKeys(bob) } returns mapOf(
            "BBBBBB" to StoredDeviceKeys(
                Signed(
                    DeviceKeys(
                        bob,
                        "BBBBBB",
                        setOf(Olm, Megolm),
                        keysOf(
                            Ed25519Key("BBBBBB", bobSigningAccount.identityKeys.ed25519),
                            Curve25519Key("BBBBBB", bobSigningAccount.identityKeys.curve25519)
                        )
                    ), mapOf()
                ), Valid(true)
            )
        )
        coEvery { store.keys.outdatedKeys } returns MutableStateFlow(setOf())
        cut = OlmSignService(UserId("me", "server"), "ABCDEF", json, store, account, utility)
    }

    afterTest {
        clearAllMocks()
    }

    afterSpec {
        account.free()
        aliceSigningAccount.free()
        bobSigningAccount.free()
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
            val signedObject = bobSigningAccountSignService.sign(
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
            cut.verify(signedObject) shouldBe VerifyResult.Valid
        }
        should("use cross signing key, when other key not found") {
            coEvery { store.keys.getDeviceKeys(alice) } returns null
            coEvery { store.keys.getCrossSigningKeys(alice) } returns setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            alice,
                            setOf(MasterKey),
                            keysOf(
                                Ed25519Key("ALICE_DEVICE", aliceSigningAccount.identityKeys.ed25519),
                                Curve25519Key("ALICE_DEVICE", aliceSigningAccount.identityKeys.curve25519)
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
            val signedObject = aliceSigningAccountSignService.sign(
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
            cut.verify(signedObject) shouldBe VerifyResult.Valid
        }
        should("return MissingSignature, when no key found") {
            coEvery { store.keys.getDeviceKeys(alice) } returns null
            coEvery { store.keys.getCrossSigningKeys(alice) } returns null
            val signedObject = aliceSigningAccountSignService.sign(
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
            cut.verify(signedObject) shouldBe VerifyResult.MissingSignature
        }
        should("verify SignedCurve25519Key") {
            val signedObject = bobSigningAccountSignService.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            )
            cut.verify(signedObject) shouldBe VerifyResult.Valid
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
            cut.verify(signedObject) shouldBe VerifyResult.Invalid("BAD_MESSAGE_MAC")
        }
    }
    context(OlmSignService::verifySelfSignedDeviceKeys.name) {
        val deviceKeys = DeviceKeys(
            userId = UserId("me", "server"),
            deviceId = "MY_DEVICE",
            algorithms = setOf(Olm, Megolm),
            keys = Keys(
                keysOf(
                    Ed25519Key("MY_DEVICE", account.identityKeys.ed25519),
                    Curve25519Key("MY_DEVICE", account.identityKeys.curve25519)
                )
            ),
        )
        val aliceDeviceKeys = aliceSigningAccountSignService.sign(
            DeviceKeys(
                userId = alice,
                deviceId = "ALICE_DEVICE",
                algorithms = setOf(Olm, Megolm),
                keys = Keys(
                    keysOf(
                        Ed25519Key("ALICE_DEVICE", aliceSigningAccount.identityKeys.ed25519),
                        Curve25519Key("ALICE_DEVICE", aliceSigningAccount.identityKeys.curve25519)
                    )
                ),
            )
        )
        should("return valid") {
            val signedDeviceKeys = Signed(
                deviceKeys,
                cut.sign(deviceKeys).signatures + aliceSigningAccountSignService.sign(deviceKeys).signatures
            )
            coEvery { store.keys.getDeviceKeys(alice) } returns mapOf(
                "ALICE_DEVICE" to StoredDeviceKeys(aliceDeviceKeys, Valid(false))
            )
            cut.verifySelfSignedDeviceKeys(signedDeviceKeys) shouldBe VerifyResult.Valid
        }
        should("return invalid when self signing signature is wrong") {
            val signedDeviceKeys = Signed(
                deviceKeys,
                mapOf((UserId("me", "server") to keysOf(Ed25519Key("MY_DEVICE", "wrong signature")))) +
                        aliceSigningAccountSignService.sign(deviceKeys).signatures
            )
            coEvery { store.keys.getDeviceKeys(alice) } returns mapOf(
                "ALICE_DEVICE" to StoredDeviceKeys(aliceDeviceKeys, Valid(false))
            )

            cut.verifySelfSignedDeviceKeys(signedDeviceKeys) shouldBe VerifyResult.Invalid("BAD_MESSAGE_MAC")
        }
        should("return invalid when others signature is wrong") {
            val signedDeviceKeys = Signed(
                deviceKeys,
                cut.sign(deviceKeys).signatures +
                        mapOf((alice to keysOf(Ed25519Key("ALICE_DEVICE", "wrong signature"))))
            )
            coEvery { store.keys.getDeviceKeys(alice) } returns mapOf(
                "ALICE_DEVICE" to StoredDeviceKeys(aliceDeviceKeys, Valid(false))
            )

            cut.verifySelfSignedDeviceKeys(signedDeviceKeys) shouldBe VerifyResult.Invalid("BAD_MESSAGE_MAC")
        }
    }
})