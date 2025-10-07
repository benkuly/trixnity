package net.folivo.trixnity.client.verification

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.ComparisonByUser
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.TheirSasStart
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class ActiveSasVerificationStateTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = LibOlmCryptoDriver

    private val keyStore = getInMemoryKeyStore()

    @Test
    fun `TheirSasStart » accept » send SasAcceptEventContent`() = runTest {
        val olmSas = driver.sas()

        var step: VerificationStep? = null
        val cut = TheirSasStart(
            content = SasStartEventContent(
                "AAAAAA",
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256, SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                transactionId = "t",
                relatesTo = null
            ),
            sasPublicKey = KeyValue.of(olmSas.publicKey),
            json = createMatrixEventJson(),
            relatesTo = null,
            transactionId = "t"
        ) { step = it }
        cut.accept()
        val result = step
        result.shouldBeInstanceOf<SasAcceptEventContent>()
        result.commitment.shouldNotBeBlank()
    }

    @Test
    fun `TheirSasStart » accept » cancel when hash not supported`() = runTest {
        val olmSas = driver.sas()
        var step: VerificationStep? = null
        val cut = TheirSasStart(
            content = SasStartEventContent(
                "AAAAAA",
                hashes = setOf(),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(
                    SasMessageAuthenticationCode.HkdfHmacSha256, SasMessageAuthenticationCode.HkdfHmacSha256V2
                ),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                transactionId = "t",
                relatesTo = null
            ),
            sasPublicKey = KeyValue.of(olmSas.publicKey),
            json = createMatrixEventJson(),
            relatesTo = null,
            transactionId = "t"
        ) { step = it }
        cut.accept()
        val result = step
        result.shouldBeInstanceOf<VerificationCancelEventContent>()
        result.code shouldBe UnknownMethod
    }

    @Test
    fun `ComparisonByUser » match send SasMacEventContent with own device key and master key`() = runTest {
        val olmSas1 = driver.sas()
        val olmSas2 = driver.sas()
        val establishedSas = olmSas1.diffieHellman(olmSas2.publicKey)
        keyStore.updateDeviceKeys(UserId("alice", "server")) {
            mapOf(
                "AAAAAA" to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            userId = UserId("alice", "server"),
                            deviceId = "AAAAAA",
                            algorithms = setOf(),
                            keys = keysOf(
                                Ed25519Key("AAKey1", "key1"), Ed25519Key("AAKey2", "key2")
                            )
                        ), mapOf()
                    ), KeySignatureTrustLevel.Valid(true)
                ), "AAAAAA_OTHER" to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            userId = UserId("alice", "server"),
                            deviceId = "AAAAAA_OTHER",
                            algorithms = setOf(),
                            keys = keysOf(
                                Ed25519Key("AAKey1_Other", "key1_other"), Ed25519Key("AAKey2_Other", "key2_other")
                            )
                        ), mapOf()
                    ), KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateCrossSigningKeys(UserId("alice", "server")) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            userId = UserId("alice", "server"),
                            usage = setOf(CrossSigningKeysUsage.MasterKey),
                            keys = keysOf(
                                Ed25519Key("AAKey3", "key3")
                            )
                        ), mapOf()
                    ), KeySignatureTrustLevel.Valid(false)
                ), StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            userId = UserId("alice", "server"),
                            usage = setOf(CrossSigningKeysUsage.SelfSigningKey),
                            keys = keysOf(
                                Ed25519Key("AAKey3_other", "key3_other")
                            )
                        ), mapOf()
                    ), KeySignatureTrustLevel.Valid(false)
                )
            )
        }
        var step: VerificationStep? = null
        val cut = ComparisonByUser(
            decimal = listOf(),
            emojis = listOf(),
            ownUserId = UserId("alice", "server"),
            ownDeviceId = "AAAAAA",
            theirUserId = UserId("bob", "server"),
            theirDeviceId = "BBBBBB",
            messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256V2,
            relatesTo = null,
            transactionId = "t",
            establishedSas = establishedSas,
            keyStore = keyStore
        ) { step = it }
        cut.match()
        val result = step
        result.shouldBeInstanceOf<SasMacEventContent>()
        result.keys.value.shouldNotBeBlank()
        result.mac shouldHaveSize 3
    }

    @Test
    fun `ComparisonByUser » match » cancel when message authentication code is not supported`() = runTest {
        // TODO
        /*
        val olmSas = Sas()
        var step: VerificationStep? = null
        val cut = ComparisonByUser(
            decimal = listOf(),
            emojis = listOf(),
            ownUserId = UserId("alice", "server"),
            ownDeviceId = "AAAAAA",
            theirUserId = UserId("bob", "server"),
            theirDeviceId = "BBBBBB",
            messageAuthenticationCode = SasMessageAuthenticationCode.Unknown("unsupported"),
            relatesTo = null,
            transactionId = "t",
            olmSas = olmSAS,
            keyStore = keyStore
        ) { step = it }
        cut.match()
        val result = step
        result.shouldBeInstanceOf<VerificationCancelEventContent>()
        result.code shouldBe UnknownMethod

         */
    }

    @Test
    fun `ComparisonByUser » noMatch`() = runTest {
        // TODO
        /*
        val olmSas = Sas()
        var step: VerificationStep? = null
        val cut = ComparisonByUser(
            decimal = listOf(),
            emojis = listOf(),
            ownUserId = UserId("alice", "server"),
            ownDeviceId = "AAAAAA",
            theirUserId = UserId("bob", "server"),
            theirDeviceId = "BBBBBB",
            messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256V2,
            relatesTo = null,
            transactionId = "t",
            olmSas = olmSAS,
            keyStore = keyStore
        ) { step = it }
        cut.noMatch()
        val result = step
        result.shouldBeInstanceOf<VerificationCancelEventContent>()
        result.code shouldBe VerificationCancelEventContent.Code.MismatchedSas

         */
    }
}