package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
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
import net.folivo.trixnity.olm.OlmSAS
import net.folivo.trixnity.olm.freeAfter

class ActiveSasVerificationStateTest : ShouldSpec({
    timeout = 30_000

    lateinit var keyStore: KeyStore
    lateinit var scope: CoroutineScope

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
    }
    afterTest {
        scope.cancel()
    }

    context(TheirSasStart::class.simpleName ?: "TheirSasStart") {
        context(TheirSasStart::accept.name) {
            should("send ${SasAcceptEventContent::class.simpleName}") {
                freeAfter(OlmSAS.create()) { olmSas ->
                    var step: VerificationStep? = null
                    val cut = TheirSasStart(
                        content = SasStartEventContent(
                            "AAAAAA",
                            hashes = setOf(SasHash.Sha256),
                            keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                            messageAuthenticationCodes = setOf(
                                SasMessageAuthenticationCode.HkdfHmacSha256,
                                SasMessageAuthenticationCode.HkdfHmacSha256V2
                            ),
                            shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                            transactionId = "t", relatesTo = null
                        ),
                        olmSas = olmSas,
                        json = createMatrixEventJson(),
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
                            keyAgreementProtocols = setOf(SasKeyAgreementProtocol.Curve25519HkdfSha256),
                            messageAuthenticationCodes = setOf(
                                SasMessageAuthenticationCode.HkdfHmacSha256,
                                SasMessageAuthenticationCode.HkdfHmacSha256V2
                            ),
                            shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                            transactionId = "t",
                            relatesTo = null
                        ),
                        olmSas = olmSas,
                        json = createMatrixEventJson(),
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
                    keyStore.updateDeviceKeys(UserId("alice", "server")) {
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
                        messageAuthenticationCode = SasMessageAuthenticationCode.HkdfHmacSha256V2,
                        relatesTo = null,
                        transactionId = "t",
                        olmSas = olmSas1,
                        keyStore = keyStore
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
            }
        }
    }
})