package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.InMemoryStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.ComparisonByUser
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.TheirSasStart
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.SasAcceptEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.SasMacEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmSAS
import net.folivo.trixnity.olm.freeAfter

class ActiveSasVerificationStateTest : ShouldSpec({
    timeout = 30_000

    lateinit var store: Store
    lateinit var storeScope: CoroutineScope

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(storeScope).apply { init() }
    }
    afterTest {
        storeScope.cancel()
    }

    context(TheirSasStart::class.simpleName ?: "TheirSasStart") {
        context(TheirSasStart::accept.name) {
            should("send ${SasAcceptEventContent::class.simpleName}") {
                freeAfter(OlmSAS.create()) { olmSas ->
                    var step: VerificationStep? = null
                    val cut = TheirSasStart(
                        content = SasStartEventContent("AAAAAA", transactionId = "t", relatesTo = null),
                        olmSas = olmSas,
                        json = createMatrixJson(),
                        relatesTo = null,
                        transactionId = "t"
                    ) { step = it }
                    cut.accept()
                    val result = step
                    result.shouldBeInstanceOf<SasAcceptEventContent>()
                    result.commitment.shouldNotBeBlank()
                }
            }
            should("cancel, when hash not supported") {
                freeAfter(OlmSAS.create()) { olmSas ->
                    var step: VerificationStep? = null
                    val cut = TheirSasStart(
                        content = SasStartEventContent(
                            "AAAAAA",
                            hashes = setOf(),
                            transactionId = "t",
                            relatesTo = null
                        ),
                        olmSas = olmSas,
                        json = createMatrixJson(),
                        relatesTo = null,
                        transactionId = "t"
                    ) { step = it }
                    cut.accept()
                    val result = step
                    result.shouldBeInstanceOf<VerificationCancelEventContent>()
                    result.code shouldBe UnknownMethod
                }
            }
        }
    }
    context(ComparisonByUser::class.simpleName ?: "ComparisonByUser") {
        context(ComparisonByUser::match.name) {
            should("send ${SasMacEventContent::class.simpleName} with own device key and master key") {
                freeAfter(OlmSAS.create(), OlmSAS.create()) { olmSas1, olmSas2 ->
                    olmSas1.setTheirPublicKey(olmSas2.publicKey)
                    store.keys.updateDeviceKeys(UserId("alice", "server")) {
                        mapOf(
                            "AAAAAA" to StoredDeviceKeys(
                                Signed(
                                    DeviceKeys(
                                        userId = UserId("alice", "server"),
                                        deviceId = "AAAAAA",
                                        algorithms = setOf(),
                                        keys = keysOf(
                                            Ed25519Key("AAKey1", "key1"),
                                            Ed25519Key("AAKey2", "key2")
                                        )
                                    ), mapOf()
                                ), KeySignatureTrustLevel.Valid(true)
                            ),
                            "AAAAAA_OTHER" to StoredDeviceKeys(
                                Signed(
                                    DeviceKeys(
                                        userId = UserId("alice", "server"),
                                        deviceId = "AAAAAA_OTHER",
                                        algorithms = setOf(),
                                        keys = keysOf(
                                            Ed25519Key("AAKey1_Other", "key1_other"),
                                            Ed25519Key("AAKey2_Other", "key2_other")
                                        )
                                    ), mapOf()
                                ), KeySignatureTrustLevel.Valid(true)
                            )
                        )
                    }
                    store.keys.updateCrossSigningKeys(UserId("alice", "server")) {
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
                            ),
                            StoredCrossSigningKeys(
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
                        messageAuthenticationCode = "hkdf-hmac-sha256",
                        relatesTo = null,
                        transactionId = "t",
                        olmSas = olmSas1,
                        store = store
                    ) { step = it }
                    cut.match()
                    val result = step
                    result.shouldBeInstanceOf<SasMacEventContent>()
                    result.keys.shouldNotBeBlank()
                    result.mac shouldHaveSize 3
                }
            }
            should("cancel, when message authentication code is not supported") {
                freeAfter(OlmSAS.create()) { olmSAS ->
                    var step: VerificationStep? = null
                    val cut = ComparisonByUser(
                        decimal = listOf(),
                        emojis = listOf(),
                        ownUserId = UserId("alice", "server"),
                        ownDeviceId = "AAAAAA",
                        theirUserId = UserId("bob", "server"),
                        theirDeviceId = "BBBBBB",
                        messageAuthenticationCode = "unsupported",
                        relatesTo = null,
                        transactionId = "t",
                        olmSas = olmSAS,
                        store = store
                    ) { step = it }
                    cut.match()
                    val result = step
                    result.shouldBeInstanceOf<VerificationCancelEventContent>()
                    result.code shouldBe UnknownMethod
                }
            }
        }
        context(ComparisonByUser::noMatch.name) {
            freeAfter(OlmSAS.create()) { olmSAS ->
                var step: VerificationStep? = null
                val cut = ComparisonByUser(
                    decimal = listOf(),
                    emojis = listOf(),
                    ownUserId = UserId("alice", "server"),
                    ownDeviceId = "AAAAAA",
                    theirUserId = UserId("bob", "server"),
                    theirDeviceId = "BBBBBB",
                    messageAuthenticationCode = "hkdf-hmac-sha256",
                    relatesTo = null,
                    transactionId = "t",
                    olmSas = olmSAS,
                    store = store
                ) { step = it }
                cut.noMatch()
                val result = step
                result.shouldBeInstanceOf<VerificationCancelEventContent>()
                result.code shouldBe VerificationCancelEventContent.Code.MismatchedSas
            }
        }
    }
})