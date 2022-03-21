package net.folivo.trixnity.client.crypto

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility
import kotlin.random.Random

@OptIn(InternalAPI::class)
class OlmSignServiceTest : ShouldSpec({

    val json = createMatrixJson()
    val account = OlmAccount.create()
    val aliceSigningAccount = OlmAccount.create()
    val bobSigningAccount = OlmAccount.create()
    val utility = OlmUtility.create()
    val ownUserId = UserId("me", "server")
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
        cut = OlmSignService(ownUserId, "ABCDEF", json, store, account, utility)
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

    context("signatures") {
        should("return signatures from device key") {
            val result = cut.signatures(JsonObject(mapOf("key" to JsonPrimitive("value"))))
            result shouldHaveSize 1
            assertSoftly(result.entries.first()) {
                key shouldBe ownUserId
                value.keys shouldHaveSize 1
                assertSoftly(value.keys.first()) {
                    this shouldBe instanceOf<Ed25519Key>()
                    require(this is Ed25519Key)
                    keyId shouldBe "ABCDEF"
                    value shouldNot beBlank()
                }
            }
        }
        should("return signatures from self signing key") {
            coEvery { store.keys.secrets } returns MutableStateFlow(
                mapOf(
                    M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(mockk(), Random.nextBytes(32).encodeBase64())
                )
            )
            coEvery { store.keys.getCrossSigningKeys(ownUserId) } returns setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            ownUserId,
                            setOf(CrossSigningKeysUsage.SelfSigningKey),
                            keysOf(
                                Ed25519Key("publicSelfSigningKey", "publicSelfSigningKey"),
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
            val result = cut.signatures(
                JsonObject(mapOf("key" to JsonPrimitive("value"))),
                OlmSignService.SignWith.AllowedSecrets(M_CROSS_SIGNING_SELF_SIGNING)
            )
            result shouldHaveSize 1
            assertSoftly(result.entries.first()) {
                key shouldBe ownUserId
                value.keys shouldHaveSize 1
                assertSoftly(value.keys.first()) {
                    this shouldBe instanceOf<Ed25519Key>()
                    require(this is Ed25519Key)
                    keyId shouldBe "publicSelfSigningKey"
                    value shouldNot beBlank()
                }
            }
        }
        should("return signatures from user signing key") {
            coEvery { store.keys.secrets } returns MutableStateFlow(
                mapOf(
                    M_CROSS_SIGNING_USER_SIGNING to StoredSecret(mockk(), Random.nextBytes(32).encodeBase64())
                )
            )
            coEvery { store.keys.getCrossSigningKeys(ownUserId) } returns setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            ownUserId,
                            setOf(CrossSigningKeysUsage.UserSigningKey),
                            keysOf(
                                Ed25519Key("publicUserSigningKey", "publicUserSigningKey"),
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
            val result = cut.signatures(
                JsonObject(mapOf("key" to JsonPrimitive("value"))),
                OlmSignService.SignWith.AllowedSecrets(M_CROSS_SIGNING_USER_SIGNING)
            )
            result shouldHaveSize 1
            assertSoftly(result.entries.first()) {
                key shouldBe ownUserId
                value.keys shouldHaveSize 1
                assertSoftly(value.keys.first()) {
                    this shouldBe instanceOf<Ed25519Key>()
                    require(this is Ed25519Key)
                    keyId shouldBe "publicUserSigningKey"
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
            assertSoftly(result.signatures!!.entries.first()) {
                key shouldBe ownUserId
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
        should("sign curve25519") {
            cut.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            ).signatures?.size shouldBe 1
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
            cut.verify(
                signedObject,
                mapOf(bob to setOf(Ed25519Key("BBBBBB", bobSigningAccount.identityKeys.ed25519)))
            ) shouldBe VerifyResult.Valid
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
            cut.verify(signedObject, mapOf(bob to setOf())).shouldBeInstanceOf<VerifyResult.MissingSignature>()
        }
        should("return MissingSignature when no signature found for sigining keys") {
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
            cut.verify(signedObject, mapOf(bob to setOf(Ed25519Key("OTHER_DEVCE", "..."))))
                .shouldBeInstanceOf<VerifyResult.MissingSignature>()
        }
        should("verify SignedCurve25519Key") {
            val signedObject = bobSigningAccountSignService.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            )
            cut.verify(
                signedObject,
                mapOf(bob to setOf(Ed25519Key("BBBBBB", bobSigningAccount.identityKeys.ed25519)))
            ) shouldBe VerifyResult.Valid
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
            cut.verify(
                signedObject,
                mapOf(bob to setOf(Ed25519Key("BBBBBB", bobSigningAccount.identityKeys.ed25519)))
            ).shouldBeInstanceOf<VerifyResult.Invalid>()
        }
    }
})