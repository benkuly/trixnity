package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.core.model.keys.MacValue
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import net.folivo.trixnity.crypto.invoke
import net.folivo.trixnity.crypto.of
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.getValue
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.suspendLazy
import kotlin.test.Test

class ActiveSasVerificationMethodTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "AAAAAA"
    private val bob = UserId("bob", "server")
    private val bobDevice = "BBBBBB"

    private val json = createMatrixEventJson()
    private val keyTrustService = KeyTrustServiceMock()
    private val sendVerificationStepFlow = MutableSharedFlow<VerificationStep>(replay = 10)
    private val keyStore = getInMemoryKeyStore()

    private val cut by suspendLazy {
        ActiveSasVerificationMethod.create(
            startEventContent = SasStartEventContent(
                aliceDevice,
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(SasMessageAuthenticationCode.HkdfHmacSha256),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null,
                transactionId = "t"
            ),
            weStartedVerification = true,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            relatesTo = null,
            transactionId = "t",
            sendVerificationStep = { sendVerificationStepFlow.emit(it) },
            keyStore = keyStore,
            keyTrustService = keyTrustService,
            json = json,
            driver = driver,
        )!!
    }

    @Test
    fun `create » cancel when key agreement protocol is not supported`() = runTest {
        val method = ActiveSasVerificationMethod.create(
            startEventContent = SasStartEventContent(
                aliceDevice,
                keyAgreementProtocols = setOf(),
                relatesTo = null,
                hashes = setOf(SasHash.Sha256),
                messageAuthenticationCodes = setOf(SasMessageAuthenticationCode.HkdfHmacSha256),
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                transactionId = "t"
            ),
            weStartedVerification = true,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            relatesTo = null,
            transactionId = "t",
            sendVerificationStep = { sendVerificationStepFlow.emit(it) },
            keyStore = keyStore,
            keyTrustService = keyTrustService,
            json = json,
            driver = driver,
        )
        method shouldBe null
        val result = sendVerificationStepFlow.first()
        result.shouldBeInstanceOf<VerificationCancelEventContent>()
        result.code shouldBe UnknownMethod
    }

    @Test
    fun `create » cancel when short authentication string is not supported`() = runTest {
        val method = ActiveSasVerificationMethod.create(
            startEventContent = SasStartEventContent(
                aliceDevice,
                relatesTo = null,
                transactionId = "t",
                shortAuthenticationString = setOf(),
                hashes = setOf(SasHash.Sha256),
                keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                messageAuthenticationCodes = setOf(SasMessageAuthenticationCode.HkdfHmacSha256),
            ),
            weStartedVerification = true,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            relatesTo = null,
            transactionId = "t",
            sendVerificationStep = { sendVerificationStepFlow.emit(it) },
            keyStore = keyStore,
            keyTrustService = keyTrustService,
            json = json,
            driver = driver,
        )
        method shouldBe null
        val result = sendVerificationStepFlow.first()
        result.shouldBeInstanceOf<VerificationCancelEventContent>()
        result.code shouldBe UnknownMethod
    }

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » cancel unexpected message SasKeyEventContent`() =
        checkNotAllowedStateChange(SasKeyEventContent(Curve25519KeyValue("key"), null, "t"))

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » cancel unexpected message SasMacEventContent`() =
        checkNotAllowedStateChange(SasMacEventContent(MacValue("keys"), keysOf(), null, "t"))

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » just set state when message is from us`() =
        runTest {
            cut.handleVerificationStep(
                SasAcceptEventContent(
                    "c",
                    hash = SasHash.Sha256,
                    keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                    messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    relatesTo = null,
                    transactionId = "t"
                ), true
            )
            cut.state.value shouldBe Accept(true)
            sendVerificationStepFlow.replayCache.shouldBeEmpty()
        }

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » send SasKeyEventContent when sender was not us`() =
        runTest {
            cut.handleVerificationStep(
                SasAcceptEventContent(
                    "c",
                    hash = SasHash.Sha256,
                    keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                    messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    relatesTo = null, transactionId = "t"
                ), false
            )
            cut.state.value shouldBe Accept(false)
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<SasKeyEventContent>()
            assertSoftly(result) {
                key.value.shouldNotBeBlank()
                relatesTo shouldBe null
                transactionId shouldBe "t"
            }
        }

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » cancel when key agreement protocol is not supported`() =
        runTest {
            cut.handleVerificationStep(
                SasAcceptEventContent(
                    "c",
                    keyAgreementProtocol = SasKeyAgreementProtocol.Unknown("c"),
                    hash = SasHash.Sha256,
                    messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    relatesTo = null,
                    transactionId = "t"
                ), false
            )
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<VerificationCancelEventContent>()
            result.code shouldBe UnknownMethod
        }

    @Test
    fun `handleVerificationStep » current state is OwnSasStart or TheirSasStart » cancel when short authentication string is not supported`() =
        runTest {
            cut.handleVerificationStep(
                SasAcceptEventContent(
                    "c",
                    shortAuthenticationString = setOf(),
                    hash = SasHash.Sha256,
                    keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                    messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                    relatesTo = null,
                    transactionId = "t"
                ), false
            )
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<VerificationCancelEventContent>()
            result.code shouldBe UnknownMethod
        }

    @Test
    fun `handleVerificationStep » current state is Accept » handle unexpected » cancel unexpected message SasAcceptEventContent`() =
        checkNotAllowedStateChange(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsAcceptHandleUnexpectedSetup
        )

    @Test
    fun `handleVerificationStep » current state is Accept » handle unexpected » cancel unexpected message SasMacEventContent`() =
        checkNotAllowedStateChange(
            SasMacEventContent(MacValue("keys"), keysOf(), null, "t"),
            ::currentStateIsAcceptHandleUnexpectedSetup
        )

    @Test
    fun `handleVerificationStep » current state is Accept » accept from them » just set state when message is from us`() =
        runTest {
            currentStateIsAcceptAcceptFromThem(cut)
            cut.handleVerificationStep(
                SasKeyEventContent(
                    Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
                ), true
            )
            cut.state.value shouldBe WaitForKeys(true)
        }

    @Test
    fun `handleVerificationStep » current state is Accept » accept from us » send SasKeyEventContent when sender was not us`() =
        runTest {
            currentStateIsAcceptAcceptFromUs(cut)
            cut.handleVerificationStep(
                SasKeyEventContent(
                    Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
                ), false
            )
            cut.state.value shouldBe WaitForKeys(false)
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<SasKeyEventContent>()
            assertSoftly(result) {
                key.value.shouldNotBeBlank()
                relatesTo shouldBe null
                transactionId shouldBe "t"
            }
        }

    @Test
    fun `handleVerificationStep » current state is Accept » accept from us » cancel when sender it not expected`() =
        runTest {
            currentStateIsAcceptAcceptFromUs(cut)
            cut.handleVerificationStep(
                SasKeyEventContent(
                    Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
                ), true
            )
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<VerificationCancelEventContent>()
            result.code shouldBe UnexpectedMessage
        }

    @Test
    fun `handleVerificationStep » current state is WaitForKeys » cancel unexpected message SasAcceptEventContent`() =
        checkNotAllowedStateChange(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsWaitForKeys
        )

    @Test
    fun `handleVerificationStep » current state is WaitForKeys » cancel unexpected message SasMacEventContent`() =
        checkNotAllowedStateChange(
            SasMacEventContent(MacValue("keys"), keysOf(), null, "t"),
            ::currentStateIsWaitForKeys
        )

    @Test
    fun `handleVerificationStep » current state is WaitForKeys » create ComparisonByUser`() = runTest {
        currentStateIsWaitForKeys(cut)
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("3vPVpNPsVYVYuozmCrihhndEvVZUHpoHBSb5+TdkaAA"),
                relatesTo = null,
                transactionId = "t"
            ), false
        )
        val state = cut.state.value
        state.shouldBeInstanceOf<ComparisonByUser>()
        state.decimal shouldHaveSize 3
        state.decimal.forEach {
            it shouldBeGreaterThanOrEqual 1000
            it shouldBeLessThanOrEqual 9191
        }
        state.emojis shouldHaveSize 7
    }

    @Test
    fun `handleVerificationStep » current state is WaitForKeys » cancel when commitment does not match`() = runTest {
        currentStateIsWaitForKeys(cut)
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), false
        )
        val result =
            sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
        result.code shouldBe MismatchedCommitment
    }

    @Test
    fun `handleVerificationStep » current state is WaitForKeys » cancel when sender it not expected`() = runTest {
        currentStateIsWaitForKeys(cut)
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), true
        )
        val result =
            sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
        result.code shouldBe UnexpectedMessage
    }

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac not received yet » cancel unexpected message SasAcceptEventContent`() =
        checkNotAllowedStateChange(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsComparisonByUserTheirMacNotReceivedYet
        )

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac not received yet » cancel unexpected message SasKeyEventContent`() =
        checkNotAllowedStateChange(
            SasKeyEventContent(Curve25519KeyValue("key"), null, "t"),
            ::currentStateIsComparisonByUserTheirMacNotReceivedYet
        )

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac not received yet » change state to WaitForMacs when accepted`() =
        runTest {
            currentStateIsComparisonByUserTheirMacNotReceivedYet(cut)
            cut.handleVerificationStep(SasMacEventContent(MacValue("keys"), keysOf(), null, "t"), true)
            cut.state.value shouldBe WaitForMacs
        }

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac not received yet » not change state to WaitForMacs when from other`() =
        runTest {
            currentStateIsComparisonByUserTheirMacNotReceivedYet(cut)
            val oldState = cut.state.value
            cut.handleVerificationStep(SasMacEventContent(MacValue("keys"), keysOf(), null, "t"), false)
            cut.state.value shouldBe oldState
        }

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac already received » cancel unexpected message SasAcceptEventContent`() =
        checkNotAllowedStateChange(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsComparisonByUserTheirMacAlreadyReceived,
        )

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac already received » cancel unexpected message SasKeyEventContent`() =
        checkNotAllowedStateChange(
            SasKeyEventContent(Curve25519KeyValue("key"), null, "t"),
            ::currentStateIsComparisonByUserTheirMacAlreadyReceived,
        )

    @Test
    fun `handleVerificationStep » current state is ComparisonByUser » their mac already received » check mac and send VerificationDoneEventContent when correct`() =
        runTest {
            currentStateIsComparisonByUserTheirMacAlreadyReceived(cut)
            cut.handleVerificationStep(SasMacEventContent(MacValue("keys"), keysOf(), null, "t"), true)
            sendVerificationStepFlow.replayCache shouldContain VerificationDoneEventContent(null, "t")
        }

    private var sasMacFromBob: VerificationStep? = null

    @Test
    fun `handleVerificationStep » current state is WaitForMacs » cancel unexpected message SasAcceptEventContent`() =
        checkNotAllowedStateChange(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ),
            ::currentStateIsWaitForMacs
        )

    @Test
    fun `handleVerificationStep » current state is WaitForMacs » cancel unexpected message SasKeyEventContent`() =
        checkNotAllowedStateChange(
            SasKeyEventContent(Curve25519KeyValue("key"), null, "t"),
            ::currentStateIsWaitForMacs
        )

    @Test
    fun `handleVerificationStep » current state is WaitForMacs » send VerificationDoneEventContent`() = runTest {
        currentStateIsWaitForMacs(cut)
        val sasMacEventContent = sasMacFromBob
        require(sasMacEventContent is SasMacEventContent)
        cut.handleVerificationStep(sasMacEventContent, false)
        sendVerificationStepFlow.replayCache shouldContain VerificationDoneEventContent(null, "t")
        keyTrustService.trustAndSignKeysCalled.value shouldBe (setOf(
            Ed25519Key(bobDevice, "bobKey"),
            Ed25519Key("HUHU", "buh"),
            Ed25519Key("AAKey3", "key3")
        ) to bob)
    }

    @Test
    fun `handleVerificationStep » current state is WaitForMacs » cancel when key mismatches`() = runTest {
        currentStateIsWaitForMacs(cut)
        val sasMacEventContent = sasMacFromBob
        require(sasMacEventContent is SasMacEventContent)
        cut.handleVerificationStep(sasMacEventContent.copy(keys = MacValue("dino")), false)
        val result =
            sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
        result.code shouldBe KeyMismatch
        result.reason shouldBe "keys mac did not match"
    }

    @Test
    fun `handleVerificationStep » current state is WaitForMacs » cancel when it contains mismatched mac`() = runTest {
        currentStateIsWaitForMacs(cut)
        val sasMacEventContent = sasMacFromBob
        require(sasMacEventContent is SasMacEventContent)
        val firstMac = sasMacEventContent.mac.first()
        cut.handleVerificationStep(
            sasMacEventContent.copy(
                mac = Keys(sasMacEventContent.mac - firstMac + Ed25519Key(firstMac.id, "dino"))
            ), false
        )
        val result =
            sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
        result.code shouldBe KeyMismatch
        result.reason shouldBe "macs did not match"
    }

    private fun checkNotAllowedStateChange(
        step: VerificationStep,
        setup: suspend (cut: ActiveSasVerificationMethod) -> Unit = {},
    ) = runTest {
        setup(cut)
        cut.handleVerificationStep(step, false)
        val result =
            sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
        result.code shouldBe UnexpectedMessage
    }

    private suspend fun currentStateIsAcceptHandleUnexpectedSetup(cut: ActiveSasVerificationMethod) {
        cut.handleVerificationStep(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), true
        )
        cut.state.value.shouldBeInstanceOf<Accept>()
    }

    private suspend fun currentStateIsAcceptAcceptFromThem(cut: ActiveSasVerificationMethod) {
        cut.handleVerificationStep(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), false
        )
        cut.state.value.shouldBeInstanceOf<Accept>()
    }

    private suspend fun currentStateIsAcceptAcceptFromUs(cut: ActiveSasVerificationMethod) {
        cut.handleVerificationStep(
            SasAcceptEventContent(
                "c",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null, transactionId = "t"
            ), true
        )
        cut.state.value.shouldBeInstanceOf<Accept>()
    }

    private suspend fun currentStateIsWaitForKeys(cut: ActiveSasVerificationMethod) {
        cut.handleVerificationStep(
            SasAcceptEventContent(
                "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null,
                transactionId = "t"
            ), false
        )
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), true
        )
        cut.state.value.shouldBeInstanceOf<WaitForKeys>()
    }

    private suspend fun currentStateIsComparisonByUserTheirMacNotReceivedYet(cut: ActiveSasVerificationMethod) {
        cut.handleVerificationStep(
            SasAcceptEventContent(
                "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null,
                transactionId = "t"
            ), false
        )
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), true
        )
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("3vPVpNPsVYVYuozmCrihhndEvVZUHpoHBSb5+TdkaAA"),
                relatesTo = null,
                transactionId = "t"
            ), false
        )
        cut.state.value.shouldBeInstanceOf<ComparisonByUser>()
    }

    private suspend fun currentStateIsComparisonByUserTheirMacAlreadyReceived(cut: ActiveSasVerificationMethod) {
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                bobDevice to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            alice, aliceDevice, setOf(Megolm),
                            keysOf(
                                Ed25519Key(aliceDevice, "aliceKey"),
                                Ed25519Key("HUHU", "buh")
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
        }
        keyStore.updateCrossSigningKeys(alice) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            userId = alice,
                            usage = setOf(CrossSigningKeysUsage.MasterKey),
                            keys = keysOf(
                                Ed25519Key("AAKey3", "key3")
                            )
                        ), mapOf()
                    ), Valid(false)
                )
            )
        }
        keyStore.updateDeviceKeys(bob) {
            mapOf(
                bobDevice to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            bob, bobDevice, setOf(Megolm),
                            keysOf(
                                Ed25519Key(bobDevice, "bobKey"),
                                Ed25519Key("HUHU", "buh")
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
        }
        keyStore.updateCrossSigningKeys(bob) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            userId = bob,
                            usage = setOf(CrossSigningKeysUsage.MasterKey),
                            keys = keysOf(
                                Ed25519Key("BBKey3", "Bkey3")
                            )
                        ), mapOf()
                    ), Valid(false)
                )
            )
        }

        val bobOlmSas = driver.sas()

        cut.handleVerificationStep(
            SasAcceptEventContent(
                "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null,
                transactionId = "t"
            ), true
        )
        cut.handleVerificationStep(
            SasKeyEventContent(KeyValue.of(bobOlmSas.publicKey), relatesTo = null, transactionId = "t"), false
        )
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), true
        )

        val alicePublicKey = sendVerificationStepFlow.filterIsInstance<SasKeyEventContent>().first().key
        val establishedSas = bobOlmSas.diffieHellman(
            driver.key.curve25519PublicKey(alicePublicKey)
        )

        var sasMacFromBob: VerificationStep? = null
        ComparisonByUser(
            listOf(),
            listOf(),
            bob,
            bobDevice,
            alice,
            aliceDevice,
            SasMessageAuthenticationCode.HkdfHmacSha256,
            null,
            "t",
            establishedSas,
            keyStore
        ) { sasMacFromBob = it }.match()
        cut.handleVerificationStep(sasMacFromBob.shouldNotBeNull(), false)
    }

    private suspend fun currentStateIsWaitForMacs(cut: ActiveSasVerificationMethod) {
        keyStore.updateDeviceKeys(bob) {
            mapOf(
                bobDevice to StoredDeviceKeys(
                    Signed(
                        DeviceKeys(
                            bob, bobDevice, setOf(Megolm),
                            keysOf(
                                Ed25519Key(bobDevice, "bobKey"),
                                Ed25519Key("HUHU", "buh")
                            )
                        ), mapOf()
                    ), Valid(true)
                )
            )
        }
        keyStore.updateCrossSigningKeys(bob) {
            setOf(
                StoredCrossSigningKeys(
                    Signed(
                        CrossSigningKeys(
                            userId = bob,
                            usage = setOf(CrossSigningKeysUsage.MasterKey),
                            keys = keysOf(
                                Ed25519Key("AAKey3", "key3")
                            )
                        ), mapOf()
                    ), Valid(false)
                )
            )
        }

        val bobOlmSas = driver.sas()

        cut.handleVerificationStep(
            SasAcceptEventContent(
                "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                hash = SasHash.Sha256,
                keyAgreementProtocol = SasKeyAgreementProtocol.Curve25519HkdfSha256,
                messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256,
                shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                relatesTo = null,
                transactionId = "t"
            ), true
        )
        cut.handleVerificationStep(
            SasKeyEventContent(KeyValue.of(bobOlmSas.publicKey), relatesTo = null, transactionId = "t"), false
        )
        cut.handleVerificationStep(
            SasKeyEventContent(
                Curve25519KeyValue("k"), relatesTo = null, transactionId = "t"
            ), true
        )
        cut.handleVerificationStep(SasMacEventContent(MacValue("keys"), keysOf(), null, "t"), true)
        cut.state.value shouldBe WaitForMacs
        val alicePublicKey = sendVerificationStepFlow.filterIsInstance<SasKeyEventContent>().first().key

        val establishedSas = bobOlmSas.diffieHellman(
            driver.key.curve25519PublicKey(alicePublicKey)
        )

        ComparisonByUser(
            listOf(),
            listOf(),
            bob,
            bobDevice,
            alice,
            aliceDevice,
            SasMessageAuthenticationCode.HkdfHmacSha256,
            null,
            "t",
            establishedSas,
            keyStore
        ) { sasMacFromBob = it }.match()
    }
}