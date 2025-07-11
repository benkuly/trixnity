package net.folivo.trixnity.crypto.sign

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beBlank
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.UnsignedRoomEventData.UnsignedStateEventData
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.olm.getOlmPublicKeys
import net.folivo.trixnity.olm.OlmAccount
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.AfterTest
import kotlin.test.Test

class SignServiceTest : TrixnityBaseTest() {

    val json = createMatrixEventJson()
    lateinit var aliceSigningAccount: OlmAccount
    val ownUserId = UserId("me", "server")
    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")

    lateinit var cut: SignServiceImpl
    lateinit var aliceSigningAccountSignService: SignServiceImpl

    private suspend fun setup() {
        aliceSigningAccount = OlmAccount.create()
        val aliceOlmKeys = getOlmPublicKeys("", aliceSigningAccount.pickle(""), aliceDevice)
        aliceSigningAccountSignService = SignServiceImpl(
            UserInfo(alice, aliceDevice, aliceOlmKeys.signingKey, aliceOlmKeys.identityKey),
            json,
            object : SignServiceStore {
                override suspend fun getOlmAccount(): String = aliceSigningAccount.pickle("")
                override suspend fun getOlmPickleKey(): String = ""
            },
        )
        val olmAccount = freeAfter(OlmAccount.create()) { it.pickle("") }
        val olmKeys = getOlmPublicKeys("", olmAccount, aliceDevice)
        cut = SignServiceImpl(
            UserInfo(ownUserId, "ABCDEF", olmKeys.signingKey, olmKeys.identityKey),
            json,
            object : SignServiceStore {
                override suspend fun getOlmAccount(): String = olmAccount
                override suspend fun getOlmPickleKey(): String = ""
            },
        )
    }

    @AfterTest
    fun free() {
        aliceSigningAccount.free()
    }

    private fun runTestWithSetup(block: suspend TestScope.() -> Unit) = runTest {
        setup()
        block()
    }

    @Test
    fun `return signatures from device key`() = runTestWithSetup {
        val result = cut.signatures(JsonObject(mapOf("key" to JsonPrimitive("value"))))
        result shouldHaveSize 1
        assertSoftly(result.entries.first()) {
            key shouldBe ownUserId
            value.keys shouldHaveSize 1
            assertSoftly(value.keys.first()) {
                this shouldBe instanceOf<Ed25519Key>()
                require(this is Ed25519Key)
                id shouldBe "ABCDEF"
                value.value shouldNot beBlank()
            }
        }
    }

    @Test
    fun `return signatures from private and public key pair`() = runTestWithSetup {
        val (privateKey, publicKey) = freeAfter(OlmPkSigning.create()) { it.privateKey to it.publicKey }
        val result = cut.signatures(
            JsonObject(mapOf("key" to JsonPrimitive("value"))),
            SignWith.KeyPair(privateKey, publicKey)
        )
        result shouldHaveSize 1
        assertSoftly(result.entries.first()) {
            key shouldBe ownUserId
            value.keys shouldHaveSize 1
            assertSoftly(value.keys.first()) {
                this shouldBe instanceOf<Ed25519Key>()
                require(this is Ed25519Key)
                id shouldBe publicKey
                value.value shouldNot beBlank()
            }
        }
    }

    @Test
    fun `ignore unsigned and signature field`() = runTestWithSetup {
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

    @Test
    fun `sign and return signed object`() = runTestWithSetup {
        val event = StateEvent(
            NameEventContent("room name"),
            EventId("\$eventId"),
            UserId("their", "server"),
            RoomId("!room:server"),
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
                id shouldBe "ABCDEF"
                value.value shouldNot beBlank()
            }
        }
    }

    @Test
    fun `ignore unsigned field`() = runTestWithSetup {
        val event1 = StateEvent(
            NameEventContent("room name"),
            EventId("\$eventId"),
            UserId("their", "server"),
            RoomId("!room:server"),
            originTimestamp = 24,
            stateKey = ""
        )
        val event2 = StateEvent(
            NameEventContent("room name"),
            EventId("\$eventId"),
            UserId("their", "server"),
            RoomId("!room:server"),
            originTimestamp = 24,
            unsigned = UnsignedStateEventData(1234),
            stateKey = ""
        )
        val result1 = cut.sign(event1)
        val result2 = cut.sign(event2)
        result1.signatures shouldBe result2.signatures
    }

    @Test
    fun `sign curve25519`() = runTestWithSetup {
        cut.signCurve25519Key(
            keyId = "AAAAAQ",
            keyValue = "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
        ).value.signatures?.size shouldBe 1
    }

    @Test
    fun `sign curve25519 with fallback`() = runTestWithSetup {
        cut.signCurve25519Key(
            keyId = "AAAAAQ",
            keyValue = "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
        ).value.signatures shouldNotBe
                cut.signCurve25519Key(
                    keyId = "AAAAAQ",
                    keyValue = "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc",
                    fallback = true,
                ).value.signatures
    }

    @Test
    fun `verify and return valid`() = runTestWithSetup {
        val signedObject = aliceSigningAccountSignService.sign(
            StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("!room:server"),
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

    @Serializable
    data class TestSign1(val field1: String)

    @Serializable
    data class TestSign2(val field1: String, val field2: String)

    @Test
    fun `verify and return valid with unknown fields`() = runTestWithSetup {
        val signedObject2 = aliceSigningAccountSignService.sign(TestSign2("value1", "value2"))
        val signedJsonObject2 = json.encodeToString(signedObject2)
        val signedObject1 = json.decodeFromString<Signed<TestSign1, UserId>>(signedJsonObject2)
        cut.verify(
            signedObject1,
            mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519)))
        ) shouldBe VerifyResult.Valid
    }

    @Test
    fun `verify and return MissingSignature when no key found`() = runTestWithSetup {
        val signedObject = aliceSigningAccountSignService.sign(
            StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("!room:server"),
                originTimestamp = 24,
                unsigned = UnsignedStateEventData(1234),
                stateKey = ""
            )
        )
        cut.verify(signedObject, mapOf(bob to setOf())).shouldBeInstanceOf<VerifyResult.MissingSignature>()
    }

    @Test
    fun `verify and return MissingSignature when no signature found for sigining keys`() = runTestWithSetup {
        val signedObject = aliceSigningAccountSignService.sign(
            StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("!room:server"),
                originTimestamp = 24,
                unsigned = UnsignedStateEventData(1234),
                stateKey = ""
            )
        )
        cut.verify(signedObject, mapOf(bob to setOf(Ed25519Key("OTHER_DEVCE", "..."))))
            .shouldBeInstanceOf<VerifyResult.MissingSignature>()
    }

    @Test
    fun `verify SignedCurve25519Key`() = runTestWithSetup {
        val signedObject = aliceSigningAccountSignService.signCurve25519Key(
            "AAAAAQ",
            "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
        ).value
        cut.verify(
            signedObject,
            mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.identityKeys.ed25519)))
        ) shouldBe VerifyResult.Valid
    }

    @Test
    fun `return invalid`() = runTestWithSetup {
        val signedObject = Signed(
            StateEvent(
                NameEventContent("room name"),
                EventId("\$eventId"),
                UserId("their", "server"),
                RoomId("!room:server"),
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