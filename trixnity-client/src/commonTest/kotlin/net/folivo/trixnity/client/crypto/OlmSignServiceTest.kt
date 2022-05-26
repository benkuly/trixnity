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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmUtility
import kotlin.random.Random

class OlmSignServiceTest : ShouldSpec({
    val json = createMatrixEventJson()
    lateinit var account: OlmAccount
    lateinit var aliceSigningAccount: OlmAccount
    lateinit var utility: OlmUtility
    val ownUserId = UserId("me", "server")
    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    lateinit var storeScope: CoroutineScope
    lateinit var store: Store

    lateinit var cut: OlmSignService
    lateinit var aliceSigningAccountSignService: OlmSignService

    beforeEach {
        account = OlmAccount.create()
        aliceSigningAccount = OlmAccount.create()
        utility = OlmUtility.create()
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        aliceSigningAccountSignService =
            OlmSignService(
                alice,
                aliceDevice,
                json,
                InMemoryStore(storeScope),
                aliceSigningAccount,
                utility
            )

        store.keys.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            alice,
                            aliceDevice,
                            setOf(Olm, Megolm),
                            keysOf(
                                Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519),
                                Curve25519Key(aliceDevice, aliceSigningAccount.identityKeys.curve25519)
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
        }
        cut = OlmSignService(ownUserId, "ABCDEF", json, store, account, utility)
    }

    afterEach {
        account.free()
        aliceSigningAccount.free()
        utility.free()
        storeScope.cancel()
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
            store.keys.secrets.value =
                mapOf(
                    M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                        Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                        Random.nextBytes(32).encodeBase64()
                    )
                )
            store.keys.updateCrossSigningKeys(ownUserId) {
                setOf(
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
            }
            val result = cut.signatures(
                JsonObject(mapOf("key" to JsonPrimitive("value"))),
                IOlmSignService.SignWith.AllowedSecrets(M_CROSS_SIGNING_SELF_SIGNING)
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
            store.keys.secrets.value = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    Event.GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())),
                    Random.nextBytes(32).encodeBase64()
                )
            )
            store.keys.updateCrossSigningKeys(ownUserId) {
                setOf(
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
            }
            val result = cut.signatures(
                JsonObject(mapOf("key" to JsonPrimitive("value"))),
                IOlmSignService.SignWith.AllowedSecrets(M_CROSS_SIGNING_USER_SIGNING)
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
            cut.verify(
                signedObject,
                mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519)))
            ) shouldBe VerifyResult.Valid
        }
        should("return MissingSignature, when no key found") {
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
            val signedObject = aliceSigningAccountSignService.signCurve25519Key(
                Curve25519Key(
                    "AAAAAQ",
                    "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
                )
            )
            cut.verify(
                signedObject,
                mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519)))
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
                    alice to keysOf(
                        Ed25519Key(
                            aliceDevice,
                            "qAwmMiFdBqJNVFnOcmIT1aIesjiecn6XHzutQZq2hGy1Z65FP7cMXRqarE/v9EinolFdli143bqwsl31fSPwBg"
                        )
                    )
                )
            )
            cut.verify(
                signedObject,
                mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519)))
            ).shouldBeInstanceOf<VerifyResult.Invalid>()
        }
    }
})