package net.folivo.trixnity.client.verification

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.key.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.olm.OlmSAS
import net.folivo.trixnity.olm.freeAfter
import kotlin.test.assertNotNull

class ActiveSasVerificationMethodTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val aliceDevice = "AAAAAA"
    val bob = UserId("bob", "server")
    val bobDevice = "BBBBBB"

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope
    lateinit var keyTrustService: KeyTrustServiceMock
    val json = createMatrixEventJson()
    lateinit var sendVerificationStepFlow: MutableSharedFlow<VerificationStep>

    lateinit var cut: ActiveSasVerificationMethod

    beforeTest {
        sendVerificationStepFlow = MutableSharedFlow(replay = 10)
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
        keyTrustService = KeyTrustServiceMock()
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
            keyTrustService = keyTrustService,
            json = json,
        )
        assertNotNull(method)
        cut = method
    }
    afterTest {
        storeScope.cancel()
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
                keyTrustService = keyTrustService,
                json = json,
            )
            method shouldBe null
            val result = sendVerificationStepFlow.first()
            result.shouldBeInstanceOf<VerificationCancelEventContent>()
            result.code shouldBe UnknownMethod
        }
    }
    suspend fun ShouldSpecContainerScope.checkNotAllowedStateChange(vararg steps: VerificationStep) {
        steps.forEach {
            should("cancel unexpected message ${it::class.simpleName}") {
                cut.handleVerificationStep(it, false)
                val result =
                    sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
                result.code shouldBe UnexpectedMessage
            }
        }
    }
    context("handleVerificationStep") {
        context("current state is ${OwnSasStart::class.simpleName} or ${TheirSasStart::class.simpleName}") {
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
                result.shouldBeInstanceOf<VerificationCancelEventContent>()
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
                    result.shouldBeInstanceOf<VerificationCancelEventContent>()
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
                val result =
                    sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
                result.code shouldBe MismatchedCommitment
            }
            should("cancel when sender it not expected") {
                cut.handleVerificationStep(SasKeyEventContent("k", relatesTo = null, transactionId = "t"), true)
                val result =
                    sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
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
            should("change state to ${WaitForMacs::class.simpleName} when accepted") {
                cut.handleVerificationStep(SasMacEventContent("keys", keysOf(), null, "t"), true)
                cut.state.value shouldBe WaitForMacs
            }
            should("not change state to ${WaitForMacs::class.simpleName} when from other") {
                val oldState = cut.state.value
                cut.handleVerificationStep(SasMacEventContent("keys", keysOf(), null, "t"), false)
                cut.state.value shouldBe oldState
            }
        }
        context("current state is ${WaitForMacs::class.simpleName}") {
            var sasMacFromBob: VerificationStep? = null
            beforeTest {
                store.keys.updateDeviceKeys(bob) {
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
                store.keys.updateCrossSigningKeys(bob) {
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
                    cut.state.value shouldBe WaitForMacs
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
            should("send ${VerificationDoneEventContent::class.simpleName}") {
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
            should("cancel when key mismatches") {
                val sasMacEventContent = sasMacFromBob
                require(sasMacEventContent is SasMacEventContent)
                cut.handleVerificationStep(sasMacEventContent.copy(keys = "dino"), false)
                val result =
                    sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
                result.code shouldBe KeyMismatch
                result.reason shouldBe "keys mac did not match"
            }
            should("cancel when it contains mismatched mac") {
                val sasMacEventContent = sasMacFromBob
                require(sasMacEventContent is SasMacEventContent)
                val firstMac = sasMacEventContent.mac.first()
                cut.handleVerificationStep(
                    sasMacEventContent.copy(
                        mac = Keys(sasMacEventContent.mac - firstMac + Ed25519Key(firstMac.keyId, "dino"))
                    ), false
                )
                val result =
                    sendVerificationStepFlow.replayCache.filterIsInstance<VerificationCancelEventContent>().first()
                result.code shouldBe KeyMismatch
                result.reason shouldBe "macs did not match"
            }
        }
    }
})