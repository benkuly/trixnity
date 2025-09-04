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
import net.folivo.trixnity.core.model.keys.KeyValue
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.crypto.olm.getOlmPublicKeys
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class SignServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = LibOlmCryptoDriver

    private val ed25519SecretKey = driver.key.ed25519SecretKey

    private val json = createMatrixEventJson()
    private val aliceSigningAccount = driver.olm.account()
    private val ownUserId = UserId("me", "server")
    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val aliceOlmKeys = driver.getOlmPublicKeys(
        pickledOlmAccount = aliceSigningAccount.pickle(),
        deviceId = aliceDevice,
    )

    private val aliceSigningAccountSignService = SignServiceImpl(
        UserInfo(alice, aliceDevice, aliceOlmKeys.signingKey, aliceOlmKeys.identityKey),
        json,
        object : SignServiceStore {
            override suspend fun getOlmAccount(): String = aliceSigningAccount.pickle()
            override suspend fun getOlmPickleKey(): String? = null
        },
        driver,
    )
    private val cut = run {
        val olmAccount = driver.olm.account()
        val pickled = olmAccount.pickle()
        val olmKeys = driver.getOlmPublicKeys(pickledOlmAccount = pickled, deviceId = aliceDevice)
        SignServiceImpl(
            UserInfo(ownUserId, "ABCDEF", olmKeys.signingKey, olmKeys.identityKey),
            json,
            object : SignServiceStore {
                override suspend fun getOlmAccount(): String = pickled
                override suspend fun getOlmPickleKey(): String? = null
            },
            driver,
        )
    }

    @Test
    fun `return signatures from device key`() = runTest {
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
    fun `return signatures from private and public key pair`() = runTest {
        val privateKey = ed25519SecretKey()
        val publicKey = privateKey.publicKey
        val result = cut.signatures(
            JsonObject(mapOf("key" to JsonPrimitive("value"))),
            SignWith.KeyPair(privateKey.base64, publicKey.base64)
        )
        result shouldHaveSize 1
        assertSoftly(result.entries.first()) {
            key shouldBe ownUserId
            value.keys shouldHaveSize 1
            assertSoftly(value.keys.first()) {
                this shouldBe instanceOf<Ed25519Key>()
                require(this is Ed25519Key)
                id shouldBe publicKey.base64
                value.value shouldNot beBlank()
            }
        }
    }

    @Test
    fun `ignore unsigned and signature field`() = runTest {
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
    fun `sign and return signed object`() = runTest {
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
    fun `ignore unsigned field`() = runTest {
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
    fun `sign curve25519`() = runTest {
        cut.signCurve25519Key(
            keyId = "AAAAAQ",
            keyValue = "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
        ).value.signatures.size shouldBe 1
    }

    @Test
    fun `sign curve25519 with fallback`() = runTest {
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
    fun `verify and return valid`() = runTest {
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
            signedObject, mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.ed25519Key.base64)))
        ) shouldBe VerifyResult.Valid
    }

    @Serializable
    data class TestSign1(val field1: String)

    @Serializable
    data class TestSign2(val field1: String, val field2: String)

    @Test
    fun `verify and return valid with unknown fields`() = runTest {
        val signedObject2 = aliceSigningAccountSignService.sign(TestSign2("value1", "value2"))
        val signedJsonObject2 = json.encodeToString(signedObject2)
        val signedObject1 = json.decodeFromString<Signed<TestSign1, UserId>>(signedJsonObject2)
        cut.verify(
            signedObject1, mapOf(alice to setOf(Ed25519Key(aliceDevice, KeyValue.of(aliceSigningAccount.ed25519Key))))
        ) shouldBe VerifyResult.Valid
    }

    @Test
    fun `verify and return MissingSignature when no key found`() = runTest {
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
    fun `verify and return MissingSignature when no signature found for sigining keys`() = runTest {
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
    fun `verify SignedCurve25519Key`() = runTest {
        val signedObject = aliceSigningAccountSignService.signCurve25519Key(
            "AAAAAQ",
            "TbzNpSurZ/tFoTukILOTRB8uB/Ko5MtsyQjCcV2fsnc"
        ).value
        cut.verify(
            signedObject, mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.ed25519Key.base64)))
        ) shouldBe VerifyResult.Valid
    }

    @Test
    fun `return invalid`() = runTest {
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
            signedObject, mapOf(alice to setOf(Ed25519Key(aliceDevice, aliceSigningAccount.ed25519Key.base64)))
        ).shouldBeInstanceOf<VerifyResult.Invalid>()
    }
}