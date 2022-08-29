package net.folivo.trixnity.crypto.sign

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.getOlmPublicKeys
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter

class SignServiceTest : ShouldSpec({
    timeout = 60_000
    val json = createMatrixEventJson()
    lateinit var aliceSigningAccount: OlmAccount
    val ownUserId = UserId("me", "server")
    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")

    lateinit var cut: SignService
    lateinit var aliceSigningAccountSignService: SignService

    beforeEach {
        aliceSigningAccount = OlmAccount.create()
        val aliceOlmKeys = getOlmPublicKeys("", aliceSigningAccount.pickle(""), aliceDevice)
        aliceSigningAccountSignService = SignService(
            UserInfo(alice, aliceDevice, aliceOlmKeys.signingKey, aliceOlmKeys.identityKey),
            json,
            object : SignServiceStore {
                override val olmAccount = aliceSigningAccount.pickle("")
                override val olmPickleKey = ""
            },
        )
        val olmAccount = freeAfter(OlmAccount.create()) { it.pickle("") }
        val olmKeys = getOlmPublicKeys("", olmAccount, aliceDevice)
        cut = SignService(
            UserInfo(ownUserId, "ABCDEF", olmKeys.signingKey, olmKeys.identityKey),
            json,
            object : SignServiceStore {
                override val olmAccount = olmAccount
                override val olmPickleKey = ""
            },
        )
    }

    afterEach {
        aliceSigningAccount.free()
    }

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
    should("return signatures from private and public key pair") {
        val (privateKey, publicKey) = freeAfter(OlmPkSigning.create()) { it.privateKey to it.publicKey }
        val result = cut.signatures(
            JsonObject(mapOf("key" to JsonPrimitive("value"))),
            SignWith.PrivateKey(privateKey, publicKey)
        )
        result shouldHaveSize 1
        assertSoftly(result.entries.first()) {
            key shouldBe ownUserId
            value.keys shouldHaveSize 1
            assertSoftly(value.keys.first()) {
                this shouldBe instanceOf<Ed25519Key>()
                require(this is Ed25519Key)
                keyId shouldBe publicKey
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
    should("sign and return signed object") {
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
        assertSoftly(result.signatures.shouldNotBeNull().entries.first()) {
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
    should("sign curve25519") {
        cut.signCurve25519Key(
            Curve25519Key(
                "AAAAAQ",
                "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
            )
        ).signatures?.size shouldBe 1
    }
    should("verify and return valid") {
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
    should("verify and return MissingSignature, when no key found") {
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
    should("verify and return MissingSignature when no signature found for sigining keys") {
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
})