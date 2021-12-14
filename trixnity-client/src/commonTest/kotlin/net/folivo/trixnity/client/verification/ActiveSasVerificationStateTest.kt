package net.folivo.trixnity.client.verification

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.ComparisonByUser
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.SasStart
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.core.model.crypto.keysOf
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.UnknownMethod
import net.folivo.trixnity.core.model.events.m.key.verification.SasAcceptEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.SasMacEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.olm.OlmSAS
import net.folivo.trixnity.olm.freeAfter

class ActiveSasVerificationStateTest : ShouldSpec({
    timeout = 30_000

    context(SasStart::class.simpleName ?: "SasStart") {
        context(SasStart::accept.name) {
            should("send ${SasAcceptEventContent::class.simpleName}") {
                freeAfter(OlmSAS.create()) { olmSas ->
                    var step: VerificationStep? = null
                    val cut = SasStart(
                        step = SasStartEventContent("AAAAAA", transactionId = "t", relatesTo = null),
                        canAccept = true,
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
                    val cut = SasStart(
                        step = SasStartEventContent("AAAAAA", hashes = setOf(), transactionId = "t", relatesTo = null),
                        canAccept = true,
                        olmSas = olmSas,
                        json = createMatrixJson(),
                        relatesTo = null,
                        transactionId = "t"
                    ) { step = it }
                    cut.accept()
                    val result = step
                    result.shouldBeInstanceOf<CancelEventContent>()
                    result.code shouldBe UnknownMethod
                }
            }
        }
    }
    context(ComparisonByUser::class.simpleName ?: "ComparisonByUser") {
        context(ComparisonByUser::match.name) {
            should("send ${SasMacEventContent::class.simpleName}") {
                freeAfter(OlmSAS.create(), OlmSAS.create()) { olmSas1, olmSas2 ->
                    olmSas1.setTheirPublicKey(olmSas2.publicKey)
                    val store = mockk<Store> {
                        coEvery { keys.getDeviceKeys(UserId("alice", "server")) }
                            .returns(
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
                                    )
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
                    result.mac shouldHaveSize 2
                }
            }
            should("cancel, when message authentication code is not supported") {
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
                    olmSas = mockk(),
                    store = mockk()
                ) { step = it }
                cut.match()
                val result = step
                result.shouldBeInstanceOf<CancelEventContent>()
                result.code shouldBe UnknownMethod
            }
        }
        context(ComparisonByUser::noMatch.name) {
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
                olmSas = mockk(),
                store = mockk()
            ) { step = it }
            cut.noMatch()
            val result = step
            result.shouldBeInstanceOf<CancelEventContent>()
            result.code shouldBe CancelEventContent.Code.MismatchedSas
        }
    }
})