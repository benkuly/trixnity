package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerContext
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmSAS
import net.folivo.trixnity.olm.freeAfter
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

class ActiveSasVerificationMethodTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"

    val store = mockk<Store>()
    val json = createMatrixJson()
    lateinit var sendVerificationStepFlow: MutableSharedFlow<VerificationStep>

    lateinit var cut: ActiveSasVerificationMethod

    beforeTest {
        sendVerificationStepFlow = MutableSharedFlow(replay = 10)
        val method = ActiveSasVerificationMethod.create(
            startEventContent = SasStartEventContent(aliceDevice, relatesTo = null, transactionId = "t"),
            weStartedVerification = true,
            ownUserId = alice,
            ownDeviceId = aliceDevice,
            theirUserId = bob,
            theirDeviceId = bobDevice,
            relatesTo = null,
            transactionId = "t",
            sendVerificationStep = { sendVerificationStepFlow.emit(it) },
            store = store,
            json = json,
            loggerFactory = LoggerFactory.default
        )
        assertNotNull(method)
        cut = method
        clearAllMocks()
    }

    context("create") {
        should("not cancel when key agreement protocol is not supported") {
            val method = ActiveSasVerificationMethod.create(
                startEventContent = SasStartEventContent(
                    aliceDevice,
                    keyAgreementProtocols = setOf(),
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
                store = store,
                json = json,
                loggerFactory = LoggerFactory.default
            )
            method shouldBe null
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<CancelEventContent>()
            result.code shouldBe UnknownMethod
        }
    }
    suspend fun ShouldSpecContainerContext.checkNotAllowedStateChange(vararg steps: VerificationStep) {
        steps.forEach {
            should("cancel unexpected message ${it::class.simpleName}") {
                cut.handleVerificationStep(it, false)
                val result = sendVerificationStepFlow.replayCache.filterIsInstance<CancelEventContent>().first()
                result.code shouldBe UnexpectedMessage
            }
        }
    }
    context("handleVerificationStep") {
        context("current state is ${SasStart::class.simpleName}") {
            checkNotAllowedStateChange(
                SasKeyEventContent("key", null, "t"),
                SasMacEventContent("keys", keysOf(), null, "t")
            )
            should("just set state when message is from us") {
                cut.handleVerificationStep(
                    SasAcceptEventContent("c", relatesTo = null, transactionId = "t"), true
                )
                cut.state.value shouldBe Accept(true)
                sendVerificationStepFlow.replayCache.shouldBeEmpty()
            }
            should("send ${SasKeyEventContent::class.simpleName} when sender was not us") {
                cut.handleVerificationStep(
                    SasAcceptEventContent("c", relatesTo = null, transactionId = "t"), false
                )
                cut.state.value shouldBe Accept(false)
                val result = sendVerificationStepFlow.first()
                result.shouldBeInstanceOf<SasKeyEventContent>()
                assertSoftly(result) {
                    key.shouldNotBeBlank()
                    relatesTo shouldBe null
                    transactionId shouldBe "t"
                }
            }
            should("cancel when key agreement protocol is not supported") {
                cut.handleVerificationStep(
                    SasAcceptEventContent(
                        "c",
                        keyAgreementProtocol = "c",
                        relatesTo = null,
                        transactionId = "t"
                    ), false
                )
                val result = sendVerificationStepFlow.first()
                result.shouldBeInstanceOf<CancelEventContent>()
                result.code shouldBe UnknownMethod
            }
        }
        context("current state is ${Accept::class.simpleName}") {
            context("handle unexpected") {
                beforeTest {
                    cut.handleVerificationStep(SasAcceptEventContent("c", relatesTo = null, transactionId = "t"), true)
                    cut.state.value.shouldBeInstanceOf<Accept>()
                }
                checkNotAllowedStateChange(
                    SasAcceptEventContent("c", relatesTo = null, transactionId = "t"),
                    SasMacEventContent("keys", keysOf(), null, "t")
                )
            }
            context("accept from them") {
                beforeTest {
                    cut.handleVerificationStep(SasAcceptEventContent("c", relatesTo = null, transactionId = "t"), false)
                    cut.state.value.shouldBeInstanceOf<Accept>()
                }
                should("just set state when message is from us") {
                    cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                    cut.state.value shouldBe WaitForKeys(true)
                }
            }
            context("accept from us") {
                beforeTest {
                    cut.handleVerificationStep(SasAcceptEventContent("c", relatesTo = null, transactionId = "t"), true)
                    cut.state.value.shouldBeInstanceOf<Accept>()
                }
                should("send ${SasKeyEventContent::class.simpleName} when sender was not us") {
                    cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), false)
                    cut.state.value shouldBe WaitForKeys(false)
                    val result = sendVerificationStepFlow.first()
                    result.shouldBeInstanceOf<SasKeyEventContent>()
                    assertSoftly(result) {
                        key.shouldNotBeBlank()
                        relatesTo shouldBe null
                        transactionId shouldBe "t"
                    }
                }
                should("cancel when sender it not expected") {
                    cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                    val result = sendVerificationStepFlow.first()
                    result.shouldBeInstanceOf<CancelEventContent>()
                    result.code shouldBe UnexpectedMessage
                }
            }
        }
        context("current state is ${WaitForKeys::class.simpleName}") {
            beforeTest {
                cut.handleVerificationStep(
                    SasAcceptEventContent(
                        "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                        relatesTo = null,
                        transactionId = "t"
                    ), false
                )
                cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                cut.state.value.shouldBeInstanceOf<WaitForKeys>()
            }
            checkNotAllowedStateChange(
                SasAcceptEventContent("c", relatesTo = null, transactionId = "t"),
                SasMacEventContent("keys", keysOf(), null, "t")
            )
            should("create ${ComparisonByUser::class.simpleName}") {
                cut.handleVerificationStep(
                    SasKeyEventContent(
                        "3vPVpNPsVYVYuozmCrihhndEvVZUHpoHBSb5+TdkaAA",
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
            should("cancel when commitment does not match") {
                cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), false)
                val result = sendVerificationStepFlow.replayCache.filterIsInstance<CancelEventContent>().first()
                result.code shouldBe MismatchedCommitment
            }
            should("cancel when sender it not expected") {
                cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                val result = sendVerificationStepFlow.replayCache.filterIsInstance<CancelEventContent>().first()
                result.code shouldBe UnexpectedMessage
            }
        }
        context("current state is ${ComparisonByUser::class.simpleName}") {
            beforeTest {
                cut.handleVerificationStep(
                    SasAcceptEventContent(
                        "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                        relatesTo = null,
                        transactionId = "t"
                    ), false
                )
                cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                cut.handleVerificationStep(
                    SasKeyEventContent(
                        "3vPVpNPsVYVYuozmCrihhndEvVZUHpoHBSb5+TdkaAA",
                        relatesTo = null,
                        transactionId = "t"
                    ), false
                )
                cut.state.value.shouldBeInstanceOf<ComparisonByUser>()
            }
            checkNotAllowedStateChange(
                SasAcceptEventContent("c", relatesTo = null, transactionId = "t"),
                SasKeyEventContent("key", null, "t")
            )
            should("change state to ${WaitForMacs::class.simpleName}") {
                cut.handleVerificationStep(SasMacEventContent("keys", keysOf(), null, "t"), true)
                cut.state.value shouldBe WaitForMacs(true)
            }
        }
        context("current state is ${WaitForMacs::class.simpleName}") {
            var sasMacFromBob: VerificationStep? = null
            beforeTest {
                coEvery { store.keys.getDeviceKeys(bob) } returns mapOf(
                    bobDevice to StoredDeviceKeys(
                        Signed(
                            DeviceKeys(
                                bob, bobDevice, setOf(Megolm),
                                keysOf(
                                    Key.Ed25519Key(bobDevice, "bobKey"),
                                    Key.Ed25519Key("HUHU", "buh")
                                )
                            ), mapOf()
                        ), Valid(true)
                    )
                )

                freeAfter(OlmSAS.create()) { bobOlmSas ->
                    cut.handleVerificationStep(
                        SasAcceptEventContent(
                            "4d8Qtr63ZuKgjhdBYdm/tZ9FiNCAAU1ZEc9HoHe6kEE",
                            relatesTo = null,
                            transactionId = "t"
                        ), true
                    )
                    cut.handleVerificationStep(
                        SasKeyEventContent(bobOlmSas.publicKey, relatesTo = null, transactionId = "t"), false
                    )
                    cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                    cut.handleVerificationStep(SasMacEventContent("keys", keysOf(), null, "t"), true)
                    cut.state.value shouldBe WaitForMacs(true)
                    val alicePublicKey = sendVerificationStepFlow.filterIsInstance<SasKeyEventContent>().first().key

                    bobOlmSas.setTheirPublicKey(alicePublicKey)
                    ComparisonByUser(
                        listOf(), listOf(),
                        bob, bobDevice, alice, aliceDevice,
                        "hkdf-hmac-sha256",
                        null, "t",
                        bobOlmSas, store
                    ) { sasMacFromBob = it }.match()
                }
            }
            checkNotAllowedStateChange(
                SasAcceptEventContent("c", relatesTo = null, transactionId = "t"),
                SasKeyEventContent("key", null, "t"),
            )
            should("send ${DoneEventContent::class.simpleName}") {
                coEvery { store.keys.saveKeyVerificationState(any(), any(), any(), any()) } just Runs
                val sasMacEventContent = sasMacFromBob
                require(sasMacEventContent is SasMacEventContent)
                cut.handleVerificationStep(sasMacEventContent, false)
                sendVerificationStepFlow.replayCache shouldContain DoneEventContent(null, "t")
                coVerify {
                    store.keys.saveKeyVerificationState(
                        Key.Ed25519Key(bobDevice, "bobKey"), bob, bobDevice,
                        KeyVerificationState.Verified("bobKey")
                    )
                    store.keys.saveKeyVerificationState(
                        Key.Ed25519Key("HUHU", "buh"), bob, bobDevice,
                        KeyVerificationState.Verified("buh")
                    )
                }
            }
            should("cancel when key mismatches") {
                val sasMacEventContent = sasMacFromBob
                require(sasMacEventContent is SasMacEventContent)
                cut.handleVerificationStep(sasMacEventContent.copy(keys = "dino"), false)
                val result = sendVerificationStepFlow.replayCache.filterIsInstance<CancelEventContent>().first()
                result.code shouldBe KeyMismatch
                result.reason shouldBe "keys mac did not match"
            }
            should("cancel when it contains mismatched mac") {
                val sasMacEventContent = sasMacFromBob
                require(sasMacEventContent is SasMacEventContent)
                val firstMac = sasMacEventContent.mac.first()
                cut.handleVerificationStep(
                    sasMacEventContent.copy(
                        mac = Keys(sasMacEventContent.mac - firstMac + Key.Ed25519Key(firstMac.keyId, "dino"))
                    ), false
                )
                val result = sendVerificationStepFlow.replayCache.filterIsInstance<CancelEventContent>().first()
                result.code shouldBe KeyMismatch
                result.reason shouldBe "macs did not match"
            }
        }
    }
})